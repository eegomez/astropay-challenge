package com.wallet.activityfeed.controller;

import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import com.wallet.activityfeed.dto.response.ApiResponse;
import com.wallet.activityfeed.dto.response.PageResponse;
import com.wallet.activityfeed.dto.response.TransactionResponse;
import com.wallet.activityfeed.exception.ResourceNotFoundException;
import com.wallet.activityfeed.usecase.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityFeedControllerTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private ActivityFeedController activityFeedController;

    private TransactionResponse transactionResponse1;
    private TransactionResponse transactionResponse2;
    private PageResponse<TransactionResponse> pageResponse;

    @BeforeEach
    void setUp() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("merchantName", "Starbucks");

        Instant now = Instant.now();

        transactionResponse1 = TransactionResponse.builder()
                .id("tx123")
                .userId("user123")
                .product(Product.CARD)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.USD)
                .description("Coffee at Starbucks")
                .sourceService("card-service")
                .metadata(metadata)
                .occurredAt(now)
                .createdAt(now)
                .build();

        transactionResponse2 = TransactionResponse.builder()
                .id("tx456")
                .userId("user123")
                .product(Product.CRYPTO)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("0.5"))
                .currency(Currency.USD)
                .description("BTC deposit")
                .sourceService("crypto-service")
                .metadata(metadata)
                .occurredAt(now.plusSeconds(60))
                .createdAt(now.plusSeconds(60))
                .build();

        List<TransactionResponse> content = Arrays.asList(transactionResponse1, transactionResponse2);
        pageResponse = PageResponse.<TransactionResponse>builder()
                .content(content)
                .nextCursor(null)
                .size(2)
                .hasMore(false)
                .build();
    }

    @Test
    void getUserTransactions_Success() {
        // Given
        String userId = "user123";
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .limit(20)
                .build();

        when(transactionService.getTransactions(any(TransactionFilterRequest.class))).thenReturn(pageResponse);

        // When
        ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> response =
                activityFeedController.getUserTransactions(userId, filters);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(2, response.getBody().getData().getSize());
        assertEquals(2, response.getBody().getData().getContent().size());
        assertFalse(response.getBody().getData().isHasMore());
        assertNull(response.getBody().getData().getNextCursor());
        assertEquals("user123", filters.getUserId());
        verify(transactionService, times(1)).getTransactions(any(TransactionFilterRequest.class));
    }

    @Test
    void getUserTransactions_WithPagination() {
        // Given
        String userId = "user123";
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .cursor("eyJzb21lIjoiY3Vyc29yIn0=")
                .limit(10)
                .build();

        PageResponse<TransactionResponse> paginatedResponse = PageResponse.<TransactionResponse>builder()
                .content(Arrays.asList(transactionResponse1))
                .nextCursor("eyJuZXh0IjoiY3Vyc29yIn0=")
                .size(1)
                .hasMore(true)
                .build();

        when(transactionService.getTransactions(any(TransactionFilterRequest.class))).thenReturn(paginatedResponse);

        // When
        ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> response =
                activityFeedController.getUserTransactions(userId, filters);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().getSize());
        assertTrue(response.getBody().getData().isHasMore());
        assertNotNull(response.getBody().getData().getNextCursor());
        verify(transactionService, times(1)).getTransactions(any(TransactionFilterRequest.class));
    }

    @Test
    void getUserTransactions_WithFilters() {
        // Given
        String userId = "user123";
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .product(Product.CARD)
                .status(TransactionStatus.COMPLETED)
                .limit(20)
                .build();

        when(transactionService.getTransactions(any(TransactionFilterRequest.class))).thenReturn(pageResponse);

        // When
        ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> response =
                activityFeedController.getUserTransactions(userId, filters);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(Product.CARD, filters.getProduct());
        assertEquals(TransactionStatus.COMPLETED, filters.getStatus());
        verify(transactionService, times(1)).getTransactions(any(TransactionFilterRequest.class));
    }

    @Test
    void getUserTransactions_EmptyResults() {
        // Given
        String userId = "user123";
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .limit(20)
                .build();

        PageResponse<TransactionResponse> emptyResponse = PageResponse.<TransactionResponse>builder()
                .content(Arrays.asList())
                .nextCursor(null)
                .size(0)
                .hasMore(false)
                .build();

        when(transactionService.getTransactions(any(TransactionFilterRequest.class))).thenReturn(emptyResponse);

        // When
        ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> response =
                activityFeedController.getUserTransactions(userId, filters);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(0, response.getBody().getData().getSize());
        assertTrue(response.getBody().getData().getContent().isEmpty());
        verify(transactionService, times(1)).getTransactions(any(TransactionFilterRequest.class));
    }

    @Test
    void getTransactionById_Success() {
        // Given
        String transactionId = "tx123";
        when(transactionService.getTransactionById(transactionId)).thenReturn(transactionResponse1);

        // When
        ResponseEntity<ApiResponse<TransactionResponse>> response =
                activityFeedController.getTransactionById(transactionId);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
        assertEquals("tx123", response.getBody().getData().getId());
        assertEquals("user123", response.getBody().getData().getUserId());
        assertEquals(Product.CARD, response.getBody().getData().getProduct());
        verify(transactionService, times(1)).getTransactionById(transactionId);
    }

    @Test
    void getTransactionById_NotFound_ThrowsException() {
        // Given
        String transactionId = "non-existent-id";
        when(transactionService.getTransactionById(transactionId))
                .thenThrow(new ResourceNotFoundException("Transaction not found with id: " + transactionId));

        // When & Then
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> activityFeedController.getTransactionById(transactionId)
        );

        assertEquals("Transaction not found with id: non-existent-id", exception.getMessage());
        verify(transactionService, times(1)).getTransactionById(transactionId);
    }
}
