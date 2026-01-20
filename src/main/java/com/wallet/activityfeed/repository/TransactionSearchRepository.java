package com.wallet.activityfeed.repository;

import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;

/**
 * Repository interface for transaction search operations
 * Uses cursor-based pagination with search_after for efficient querying
 */
public interface TransactionSearchRepository {

    /**
     * Index a transaction for search
     *
     * @param transaction Transaction to index
     */
    void index(Transaction transaction);

    /**
     * Search transactions with complex filters and cursor-based pagination
     *
     * @param filters Filter criteria including cursor
     * @return CursorResult with matching transactions and next cursor
     */
    CursorResult<Transaction> search(TransactionFilterRequest filters);
}
