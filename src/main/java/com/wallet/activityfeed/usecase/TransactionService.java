package com.wallet.activityfeed.usecase;

import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import com.wallet.activityfeed.dto.response.PageResponse;
import com.wallet.activityfeed.dto.response.TransactionResponse;
import com.wallet.activityfeed.exception.ResourceNotFoundException;
import com.wallet.activityfeed.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transaction service using polyglot persistence strategy
 * - Transactions are created via SQS events (not through this service)
 * - Reads are routed via TransactionQueryRouter based on filter complexity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionQueryRouter queryRouter;
    private final TransactionMapper transactionMapper;
    private final TransactionRepository transactionRepository;

    /**
     * Query transactions using smart routing with cursor-based pagination
     * - DynamoDB: for simple userId queries (with optional date range)
     * - OpenSearch: for complex filters (product, status, currency, text search, metadata)
     */
    public PageResponse<TransactionResponse> getTransactions(TransactionFilterRequest filters) {

        if (filters.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }

        log.info("Fetching transactions with filters: userId={}, cursor={}, limit={}",
                filters.getUserId(), filters.getCursor() != null, filters.getLimit());

        // Use query router to decide which data store to use
        CursorResult<Transaction> result = queryRouter.query(filters);

        List<TransactionResponse> content = result.getItems().stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<TransactionResponse>builder()
                .content(content)
                .nextCursor(result.getNextCursor())
                .size(content.size())
                .hasMore(result.getNextCursor() != null)
                .build();
    }

    /**
     * Get a specific transaction by ID from DynamoDB
     */
    public TransactionResponse getTransactionById(String id) {
        log.info("Fetching transaction by id: {}", id);

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        return transactionMapper.toResponse(transaction);
    }
}
