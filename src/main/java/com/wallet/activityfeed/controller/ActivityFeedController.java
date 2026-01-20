package com.wallet.activityfeed.controller;

import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import com.wallet.activityfeed.dto.response.ApiResponse;
import com.wallet.activityfeed.dto.response.PageResponse;
import com.wallet.activityfeed.dto.response.TransactionResponse;
import com.wallet.activityfeed.usecase.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activity-feed")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activity Feed", description = "Unified activity feed API for all financial transactions")
public class ActivityFeedController {

    private final TransactionService transactionService;

    @GetMapping("/users/{userId}/transactions")
    @Operation(
            summary = "Get user transactions",
            description = "Retrieves paginated and filtered transactions for a specific user. " +
                    "Uses DynamoDB for simple queries (userId only or userId + date) or OpenSearch for complex filters."
    )
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getUserTransactions(
            @Parameter(description = "User ID")
            @PathVariable String userId,
            @Parameter(description = "Transaction filter criteria")
            @ModelAttribute TransactionFilterRequest filters) {

        log.info("Received request to fetch transactions for user: {}, sortBy={}, sortDirection={}, filters={}",
                userId, filters.getSortBy(), filters.getSortDirection(), filters);
        filters.setUserId(userId);
        PageResponse<TransactionResponse> response = transactionService.getTransactions(filters);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transactions/{id}")
    @Operation(
            summary = "Get transaction by ID",
            description = "Retrieves a specific transaction by its ID from DynamoDB using the id-index GSI. " +
                    "Fast lookup using Global Secondary Index for O(1) performance."
    )
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @Parameter(description = "Transaction ID")
            @PathVariable String id) {

        log.info("Received request to fetch transaction by id: {}", id);
        TransactionResponse response = transactionService.getTransactionById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
