package com.wallet.activityfeed.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OpenSearch implementation of TransactionSearchRepository with cursor-based pagination
 * Uses search_after for efficient deep pagination
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class OpenSearchTransactionRepository implements TransactionSearchRepository {

    @Value("${aws.opensearch.index-name:activity_items}")
    private String indexName;

    private static final String KEYWORD_SUFFIX = ".keyword";

    private final RestHighLevelClient openSearchClient;
    private final ObjectMapper objectMapper;

    @Override
    public void index(Transaction transaction) {
        try {
            log.debug("Indexing transaction in OpenSearch: {}", transaction.getId());

            Map<String, Object> source = objectMapper.convertValue(transaction, Map.class);
            convertMetadataJsonToMap(source, transaction.getId());

            String jsonString = objectMapper.writeValueAsString(source);

            IndexRequest request = new IndexRequest(indexName)
                    .id(transaction.getId())
                    .source(jsonString, XContentType.JSON);

            openSearchClient.index(request, RequestOptions.DEFAULT);

        } catch (IOException e) {
            log.error("Error indexing transaction in OpenSearch: {}", transaction.getId(), e);
            throw new RuntimeException("Failed to index transaction in OpenSearch", e);
        }
    }

    @Override
    public CursorResult<Transaction> search(TransactionFilterRequest filters) {
        try {
            log.debug("Searching transactions in OpenSearch with filters: {}", filters);

            SearchRequest searchRequest = new SearchRequest(indexName);
            SearchSourceBuilder searchSourceBuilder = buildSearchQuery(filters);
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

            List<Transaction> transactions = new ArrayList<>();

            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // Convert metadata object back to metadataJson string for Transaction entity
                convertMetadataMapToJson(sourceAsMap);

                Transaction transaction = objectMapper.convertValue(sourceAsMap, Transaction.class);
                transactions.add(transaction);
            }

            // Determine if there are more results by checking if we got limit + 1 items
            String nextCursor = null;
            List<Transaction> itemsToReturn = transactions;

            if (transactions.size() > filters.getLimit()) {
                // More results exist - return only 'limit' items and create cursor from last returned item
                itemsToReturn = transactions.subList(0, filters.getLimit());
                // Get sort values from the last item we're returning
                SearchHit lastHit = response.getHits().getHits()[filters.getLimit() - 1];
                nextCursor = encodeCursor(lastHit.getSortValues());
            }

            log.debug("Found {} transactions, hasMore: {}", itemsToReturn.size(), nextCursor != null);
            return CursorResult.<Transaction>builder()
                    .items(itemsToReturn)
                    .nextCursor(nextCursor)
                    .build();

        } catch (IOException e) {
            log.error("Error searching transactions in OpenSearch", e);
            throw new RuntimeException("Failed to search transactions in OpenSearch", e);
        }
    }

    /**
     * Build OpenSearch query from filters with cursor support
     */
    private SearchSourceBuilder buildSearchQuery(TransactionFilterRequest filters) {
        BoolQueryBuilder boolQuery = buildFilters(filters);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQuery);

        applyPagination(searchSourceBuilder, filters);
        applySorting(searchSourceBuilder, filters);

        return searchSourceBuilder;
    }

    /**
     * Build boolean query with all filters applied
     */
    private BoolQueryBuilder buildFilters(TransactionFilterRequest filters) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Required: userId filter
        boolQuery.must(QueryBuilders.termQuery("userId", filters.getUserId()));

        // Optional: product filter
        if (filters.getProduct() != null) {
            boolQuery.must(QueryBuilders.termQuery("product", filters.getProduct().name()));
        }

        // Optional: type filter
        if (filters.getType() != null) {
            boolQuery.must(QueryBuilders.termQuery("type", filters.getType().name()));
        }

        // Optional: status filter
        if (filters.getStatus() != null) {
            boolQuery.must(QueryBuilders.termQuery("status", filters.getStatus().name()));
        }

        // Optional: currency filter
        if (filters.getCurrency() != null) {
            boolQuery.must(QueryBuilders.termQuery("currency", filters.getCurrency().name()));
        }

        // Optional: date range filter
        if (filters.getStartDate() != null || filters.getEndDate() != null) {
            var rangeQuery = QueryBuilders.rangeQuery("occurredAt");
            if (filters.getStartDate() != null) {
                rangeQuery.gte(filters.getStartDate().toString());
            }
            if (filters.getEndDate() != null) {
                rangeQuery.lte(filters.getEndDate().toString());
            }
            boolQuery.must(rangeQuery);
        }

        // Optional: search text (across description, eventId, and transactionId)
        if (filters.getSearchText() != null && !filters.getSearchText().isEmpty()) {
            boolQuery.must(
                    QueryBuilders.multiMatchQuery(filters.getSearchText())
                            .field("description")
                            .field("eventId")
                            .field("transactionId")
            );
        }

        // Optional: metadata field search
        if (filters.getMetadataField() != null && filters.getMetadataValue() != null) {
            String fieldPath = "metadata." + filters.getMetadataField() + KEYWORD_SUFFIX;
            boolQuery.must(QueryBuilders.termQuery(fieldPath, filters.getMetadataValue()));
        }

        return boolQuery;
    }

    /**
     * Apply cursor-based pagination to search query
     */
    private void applyPagination(SearchSourceBuilder searchSourceBuilder, TransactionFilterRequest filters) {
        searchSourceBuilder.size(filters.getLimit() + 1);

        if (filters.getCursor() != null) {
            Object[] searchAfter = decodeCursor(filters.getCursor());
            searchSourceBuilder.searchAfter(searchAfter);
        }
    }

    /**
     * Apply sorting to search query with tie-breaker
     */
    private void applySorting(SearchSourceBuilder searchSourceBuilder, TransactionFilterRequest filters) {
        SortOrder sortOrder = filters.getSortDirection().equalsIgnoreCase("ASC")
                ? SortOrder.ASC
                : SortOrder.DESC;
        searchSourceBuilder.sort(filters.getSortBy(), sortOrder);
        searchSourceBuilder.sort("id", SortOrder.ASC);
    }

    /**
     * Encode sort values as Base64 cursor
     */
    private String encodeCursor(Object[] sortValues) {
        try {
            String json = objectMapper.writeValueAsString(sortValues);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            log.error("Failed to encode cursor", e);
            return null;
        }
    }

    /**
     * Decode Base64 cursor to sort values
     */
    private Object[] decodeCursor(String cursor) {
        try {
            String json = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, Object[].class);
        } catch (Exception e) {
            log.error("Failed to decode cursor: {}", cursor, e);
            throw new IllegalArgumentException("Invalid cursor format", e);
        }
    }

    /**
     * Convert metadataJson string to metadata Map for OpenSearch indexing
     */
    private void convertMetadataJsonToMap(Map<String, Object> source, String transactionId) {
        if (source.containsKey("metadataJson") && source.get("metadataJson") != null) {
            String metadataJson = (String) source.get("metadataJson");
            try {
                Map<String, Object> metadataMap = objectMapper.readValue(
                        metadataJson,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {}
                );
                source.remove("metadataJson");
                source.put("metadata", metadataMap);
            } catch (IOException e) {
                log.warn("Failed to parse metadataJson for OpenSearch: {}", transactionId, e);
                source.remove("metadataJson");
            }
        }
    }

    /**
     * Convert metadata Map to metadataJson string for Transaction entity
     */
    private void convertMetadataMapToJson(Map<String, Object> source) {
        if (source.containsKey("metadata") && source.get("metadata") != null) {
            try {
                String metadataJson = objectMapper.writeValueAsString(source.get("metadata"));
                source.put("metadataJson", metadataJson);
                source.remove("metadata");
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata from OpenSearch", e);
            }
        }
    }
}
