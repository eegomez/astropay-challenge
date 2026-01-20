package com.wallet.activityfeed.repository;

import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository interface for transaction persistence operations
 * Uses cursor-based pagination for efficient querying
 */
public interface TransactionRepository {

    /**
     * Save transaction with idempotent conditional write
     *
     * @param transaction Transaction to save
     * @return Saved transaction
     * @throws ConditionalCheckFailedException if transaction already exists (idempotent - safe to ignore)
     */
    Transaction save(Transaction transaction);

    /**
     * Find transaction by ID
     *
     * @param id Transaction ID
     * @return Optional containing the transaction if found
     */
    Optional<Transaction> findById(String id);

    /**
     * Query transactions by userId with cursor-based pagination
     *
     * @param userId User ID
     * @param cursor Pagination cursor (null for first page)
     * @param limit Maximum number of results
     * @return CursorResult with transactions and next cursor
     */
    CursorResult<Transaction> findByUserId(String userId, String cursor, int limit);

    /**
     * Query transactions by userId and date range with cursor-based pagination
     *
     * @param userId User ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @param cursor Pagination cursor (null for first page)
     * @param limit Maximum number of results
     * @return CursorResult with transactions and next cursor
     */
    CursorResult<Transaction> findByUserIdAndDateRange(
            String userId,
            Instant startDate,
            Instant endDate,
            String cursor,
            int limit
    );

    /**
     * Delete transaction by ID
     *
     * @param id Transaction ID
     */
    void deleteById(String id);
}
