package com.wallet.activityfeed.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * DynamoDB implementation of TransactionRepository with cursor-based pagination
 *
 * Table schema:
 *  - Table: transactions
 *  - PK: user_id (userId)
 *  - SK: sk (format: "occurredAt#id")
 *  - GSI: id-index (PK: id)
 *
 * Cursor format: Base64 encoded JSON containing LastEvaluatedKey
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DynamoDbTransactionRepository implements TransactionRepository {

    @Value("${aws.dynamodb.table-name:transactions}")
    private String tableName;

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final ObjectMapper objectMapper;

    private DynamoDbTable<Transaction> getTable() {
        return dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(Transaction.class));
    }

    @Override
    public Transaction save(Transaction transaction) {
        log.debug("Saving transaction to DynamoDB: userId={}, sk={}, id={}",
                transaction.getUserId(), transaction.getSk(), transaction.getId());

        // Idempotency: prevent duplicate writes using sk as condition
        Expression conditionExpression = Expression.builder()
                .expression("attribute_not_exists(sk)")
                .build();

        PutItemEnhancedRequest<Transaction> request = PutItemEnhancedRequest.builder(Transaction.class)
                .item(transaction)
                .conditionExpression(conditionExpression)
                .build();

        try {
            getTable().putItem(request);
            log.debug("Transaction saved successfully: {}", transaction.getId());
            return transaction;
        } catch (ConditionalCheckFailedException e) {
            log.warn("Transaction already exists (idempotent duplicate): userId={}, sk={}",
                    transaction.getUserId(), transaction.getSk());
            throw e;
        }
    }

    @Override
    public Optional<Transaction> findById(String id) {
        log.debug("Finding transaction by id: {} (using GSI id-index)", id);

        // Use GSI to query by transaction id (much faster than table scan)
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue(id)
                        .build());

        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(1)
                .build();

        // Query the GSI index
        Iterator<Page<Transaction>> pageIterator = getTable()
                .index("id-index")
                .query(queryRequest)
                .iterator();

        if (pageIterator.hasNext()) {
            Page<Transaction> page = pageIterator.next();
            List<Transaction> items = page.items();
            if (!items.isEmpty()) {
                log.debug("Found transaction with id: {}", id);
                return Optional.of(items.get(0));
            }
        }

        log.debug("Transaction not found with id: {}", id);
        return Optional.empty();
    }

    @Override
    public CursorResult<Transaction> findByUserId(String userId, String cursor, int limit) {
        log.debug("Querying transactions by userId: userId={}, cursor={}, limit={}", userId, cursor != null, limit);

        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue(userId)
                        .build());

        // Fetch limit + 1 to determine if there are more results
        // Default to DESC (scanIndexForward=false) for newest first
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit + 1)
                .scanIndexForward(false); // DESC order (newest first) - sortDirection not configurable for userId-only queries

        // Add exclusive start key if cursor provided
        if (cursor != null) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        // Execute query and check if results exist
        Iterator<Page<Transaction>> iterator = getTable().query(requestBuilder.build()).iterator();
        if (!iterator.hasNext()) {
            log.debug("No transactions found for userId: {}", userId);
            return CursorResult.<Transaction>builder()
                    .items(Collections.emptyList())
                    .nextCursor(null)
                    .build();
        }

        Page<Transaction> page = iterator.next();
        List<Transaction> transactions = page.items();

        CursorResult<Transaction> result = buildCursorResult(transactions, limit);
        log.debug("Found {} transactions for userId: {}, hasMore: {}",
                result.getItems().size(), userId, result.getNextCursor() != null);
        return result;
    }

    @Override
    public CursorResult<Transaction> findByUserIdAndDateRange(
            String userId,
            Instant startDate,
            Instant endDate,
            String cursor,
            int limit) {

        log.debug("Querying transactions by userId and date range: userId={}, start={}, end={}, cursor={}, limit={}",
                userId, startDate, endDate, cursor != null, limit);

        // SK format is "occurredAt#id", so we can use string comparison for date range
        // Using tilde (~) as suffix ensures we catch all timestamps on the end date, since
        // all valid ISO8601 and '#' characters have lower ASCII values than '~' (ASCII 126)
        String startSk = startDate != null ? startDate.toString() : "1970-01-01T00:00:00Z";
        String endSk = endDate != null ? endDate.toString() + "~" : "9999-12-31T23:59:59Z";

        QueryConditional queryConditional = QueryConditional
                .sortBetween(
                        Key.builder()
                                .partitionValue(userId)
                                .sortValue(startSk)
                                .build(),
                        Key.builder()
                                .partitionValue(userId)
                                .sortValue(endSk)
                                .build()
                );

        // Fetch limit + 1 to determine if there are more results
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit + 1)
                .scanIndexForward(false); // Descending order (newest first)

        // Add exclusive start key if cursor provided
        if (cursor != null) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        // Execute query and check if results exist
        Iterator<Page<Transaction>> iterator = getTable().query(requestBuilder.build()).iterator();
        if (!iterator.hasNext()) {
            log.debug("No transactions found for userId: {} in date range", userId);
            return CursorResult.<Transaction>builder()
                    .items(Collections.emptyList())
                    .nextCursor(null)
                    .build();
        }

        Page<Transaction> page = iterator.next();
        List<Transaction> transactions = page.items();

        CursorResult<Transaction> result = buildCursorResult(transactions, limit);
        log.debug("Found {} transactions for userId: {} in date range, hasMore: {}",
                result.getItems().size(), userId, result.getNextCursor() != null);
        return result;
    }

    @Override
    public void deleteById(String id) {
        log.warn("deleteById is not efficiently supported with current schema (would require scan or GSI)");
        throw new UnsupportedOperationException(
                "deleteById is not supported with current schema. Use deleteByUserIdAndSk instead.");
    }

    /**
     * Encode DynamoDB LastEvaluatedKey as Base64 cursor
     */
    private String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        try {
            // Convert AttributeValue map to simple map for serialization
            Map<String, String> cursorMap = new HashMap<>();
            lastEvaluatedKey.forEach((key, value) -> {
                if (value.s() != null) {
                    cursorMap.put(key, value.s());
                }
            });

            String json = objectMapper.writeValueAsString(cursorMap);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            log.error("Failed to encode cursor", e);
            throw new RuntimeException("Failed to encode pagination cursor", e);
        }
    }

    /**
     * Decode Base64 cursor to DynamoDB LastEvaluatedKey
     */
    private Map<String, AttributeValue> decodeCursor(String cursor) {
        try {
            String json = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, String> cursorMap = objectMapper.readValue(json, Map.class);

            // Convert simple map back to AttributeValue map
            Map<String, AttributeValue> lastEvaluatedKey = new HashMap<>();
            cursorMap.forEach((key, value) ->
                    lastEvaluatedKey.put(key, AttributeValue.builder().s(value).build())
            );

            return lastEvaluatedKey;
        } catch (Exception e) {
            log.error("Failed to decode cursor: {}", cursor, e);
            throw new IllegalArgumentException("Invalid cursor format", e);
        }
    }

    /**
     * Create a cursor from a transaction's keys
     */
    private String createCursorFromTransaction(Transaction transaction) {
        Map<String, AttributeValue> lastKey = new HashMap<>();
        lastKey.put("user_id", AttributeValue.builder().s(transaction.getUserId()).build());
        lastKey.put("sk", AttributeValue.builder().s(transaction.getSk()).build());
        return encodeCursor(lastKey);
    }

    /**
     * Build cursor result with limit+1 pagination logic
     */
    private CursorResult<Transaction> buildCursorResult(List<Transaction> transactions, int limit) {
        String nextCursor = null;
        List<Transaction> itemsToReturn = transactions;

        if (transactions.size() > limit) {
            // More results exist - return only 'limit' items and create cursor from last item
            itemsToReturn = transactions.subList(0, limit);
            Transaction lastItem = itemsToReturn.get(itemsToReturn.size() - 1);
            nextCursor = createCursorFromTransaction(lastItem);
        }

        return CursorResult.<Transaction>builder()
                .items(itemsToReturn)
                .nextCursor(nextCursor)
                .build();
    }
}
