package com.wallet.activityfeed.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.event.TransactionEvent;
import com.wallet.activityfeed.repository.TransactionRepository;
import com.wallet.activityfeed.repository.TransactionSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;

/**
 * Processes transaction events from SQS and stores them in both the primary data store and search engine
 * Implements dual-write pattern for polyglot persistence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProcessor {

    private final TransactionRepository transactionRepository;
    private final TransactionSearchRepository searchRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process transaction event:
     * 1. Convert event to Transaction entity
     * 2. Store in DynamoDB with conditional write (idempotent)
     * 3. Index in OpenSearch (for complex searches)
     *
     * Implements idempotency via DynamoDB conditional write (attribute_not_exists)
     * Duplicate events are safely ignored without race conditions
     */
    public void processEvent(TransactionEvent event) {
        log.info("Processing transaction event: eventId={}, transactionId={}",
                event.getEventId(), event.getPayload().getTransactionId());

        Transaction transaction = mapEventToTransaction(event);

        try {
            // Atomic conditional write to primary data store (prevents duplicates at DB level)
            transactionRepository.save(transaction);
            log.debug("Saved transaction to primary data store: {}", transaction.getId());

            // Only index in search repository if primary write succeeded
            try {
                searchRepository.index(transaction);
                log.debug("Indexed transaction in search repository: {}", transaction.getId());
            } catch (Exception e) {
                // Log but don't fail - search repository is secondary store
                // Transaction is already in primary data store (source of truth)
                log.error("Failed to index in search repository (non-fatal): {}", transaction.getId(), e);
            }

            log.info("Successfully processed transaction event: {}", transaction.getId());

        } catch (ConditionalCheckFailedException e) {
            // Duplicate event - already processed (idempotent success)
            log.info("Duplicate transaction event ignored (idempotent): transactionId={}, eventId={}",
                    transaction.getId(), event.getEventId());
            // Don't re-throw - this is expected and safe
        } catch (Exception e) {
            log.error("Failed to process transaction event: eventId={}", event.getEventId(), e);
            throw new RuntimeException("Failed to process transaction event", e);
        }
    }

    private Transaction mapEventToTransaction(TransactionEvent event) {
        TransactionEvent.TransactionEventPayload payload = event.getPayload();

        // Use transactionId from payload if available, otherwise use eventId for deterministic deduplication
        String id = payload.getTransactionId() != null ?
                payload.getTransactionId() :
                event.getEventId();

        // Determine occurredAt
        Instant occurredAt = payload.getOccurredAt() != null ? payload.getOccurredAt() : Instant.now();

        // Build sort key: occurredAt#id
        String sk = occurredAt.toString() + "#" + id;

        String metadataJson = null;
        if (payload.getMetadata() != null && !payload.getMetadata().isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(payload.getMetadata());
            } catch (Exception e) {
                log.warn("Failed to serialize metadata for transaction: {}", id, e);
            }
        }

        return Transaction.builder()
                .userId(payload.getUserId())
                .sk(sk)                                                // Sort key: occurredAt#id
                .id(id)                                                // Regular attribute
                .product(payload.getProduct())
                .type(payload.getType())
                .status(payload.getStatus())
                .amount(payload.getAmount())
                .currency(payload.getCurrency())
                .description(payload.getDescription())
                .occurredAt(occurredAt)
                .createdAt(Instant.now())
                .sourceService(event.getSourceService())
                .eventId(event.getEventId())
                .transactionId(payload.getTransactionId())
                .metadataJson(metadataJson)
                .build();
    }
}
