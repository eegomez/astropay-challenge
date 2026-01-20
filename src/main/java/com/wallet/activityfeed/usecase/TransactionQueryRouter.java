package com.wallet.activityfeed.usecase;

import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import com.wallet.activityfeed.repository.TransactionRepository;
import com.wallet.activityfeed.repository.TransactionSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Smart query router that decides whether to use the primary data store or search engine
 * based on the filter complexity
 *
 * Strategy:
 * - Use TransactionRepository (DynamoDB): ONLY when filtering by userId + date (simple, fast queries)
 * - Use TransactionSearchRepository (OpenSearch): When ANY additional filter is present (complex searches)
 *
 * Both use cursor-based pagination for efficient deep pagination
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionQueryRouter {

    private final TransactionRepository transactionRepository;
    private final TransactionSearchRepository searchRepository;

    /**
     * Route query to appropriate data store based on filter complexity
     * Returns cursor-based result
     */
    public CursorResult<Transaction> query(TransactionFilterRequest filters) {
        if (shouldUseTransactionRepository(filters)) {
            log.info("Routing query to DynamoDB TransactionRepository: sortBy={}, sortDirection={}",
                    filters.getSortBy(), filters.getSortDirection());
            return queryTransactionRepository(filters);
        } else {
            log.info("Routing query to OpenSearch SearchRepository: sortBy={}, sortDirection={}, hasFilters={}",
                    filters.getSortBy(), filters.getSortDirection(),
                    (filters.getProduct() != null || filters.getStatus() != null || filters.getSearchText() != null));
            return querySearchRepository(filters);
        }
    }

    /**
     * Determine if TransactionRepository (simple store) should be used
     * TransactionRepository is used ONLY when:
     * - Only userId filter is present (with or without date range)
     * - No other filters like product, type, status, currency, searchText, or metadata
     * - Sorting is by occurredAt DESC (DynamoDB can only sort by sort key in DESC order)
     */
    private boolean shouldUseTransactionRepository(TransactionFilterRequest filters) {
        return filters.getUserId() != null
                && filters.getProduct() == null
                && filters.getType() == null
                && filters.getStatus() == null
                && filters.getCurrency() == null
                && (filters.getSearchText() == null || filters.getSearchText().isEmpty())
                && filters.getMetadataField() == null
                && filters.getMetadataValue() == null
                && "occurredAt".equals(filters.getSortBy()) // DynamoDB can only sort by sort key
                && "DESC".equalsIgnoreCase(filters.getSortDirection()); // DynamoDB is hardcoded to DESC
    }

    private CursorResult<Transaction> queryTransactionRepository(TransactionFilterRequest filters) {
        String userId = filters.getUserId();
        String cursor = filters.getCursor();
        int limit = filters.getLimit();

        if (filters.getStartDate() != null || filters.getEndDate() != null) {
            return transactionRepository.findByUserIdAndDateRange(
                    userId,
                    filters.getStartDate(),
                    filters.getEndDate(),
                    cursor,
                    limit
            );
        } else {
            return transactionRepository.findByUserId(userId, cursor, limit);
        }
    }

    private CursorResult<Transaction> querySearchRepository(TransactionFilterRequest filters) {
        return searchRepository.search(filters);
    }
}
