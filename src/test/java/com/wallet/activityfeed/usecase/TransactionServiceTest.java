package com.wallet.activityfeed.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import com.wallet.activityfeed.dto.response.PageResponse;
import com.wallet.activityfeed.dto.response.TransactionResponse;
import com.wallet.activityfeed.exception.ResourceNotFoundException;
import com.wallet.activityfeed.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionQueryRouter queryRouter;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Transaction transaction1;
    private Transaction transaction2;
    private TransactionResponse response1;
    private TransactionResponse response2;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("merchantName", "Starbucks");
        String metadataJson = objectMapper.writeValueAsString(metadata);

        Instant now = Instant.now();
        String sk1 = now.toString() + "#tx123";
        String sk2 = now.plusSeconds(60).toString() + "#tx456";

        transaction1 = Transaction.builder()
                .id("tx123")
                .userId("user123")
                .sk(sk1)
                .product(Product.CARD)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.USD)
                .description("Coffee at Starbucks")
                .sourceService("card-service")
                .metadataJson(metadataJson)
                .occurredAt(now)
                .createdAt(now)
                .build();

        transaction2 = Transaction.builder()
                .id("tx456")
                .userId("user123")
                .sk(sk2)
                .product(Product.CRYPTO)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("0.5"))
                .currency(Currency.USD)
                .description("BTC deposit")
                .sourceService("crypto-service")
                .metadataJson(metadataJson)
                .occurredAt(now.plusSeconds(60))
                .createdAt(now.plusSeconds(60))
                .build();

        response1 = TransactionResponse.builder()
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
                .build();

        response2 = TransactionResponse.builder()
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
                .build();
    }

    @Test
    void getTransactions_Success() {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .limit(20)
                .build();

        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        CursorResult<Transaction> cursorResult = CursorResult.<Transaction>builder()
                .items(transactions)
                .nextCursor(null)
                .build();

        when(queryRouter.query(any(TransactionFilterRequest.class))).thenReturn(cursorResult);
        when(transactionMapper.toResponse(transaction1)).thenReturn(response1);
        when(transactionMapper.toResponse(transaction2)).thenReturn(response2);

        // When
        PageResponse<TransactionResponse> result = transactionService.getTransactions(filters);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getSize());
        assertEquals(2, result.getContent().size());
        assertNull(result.getNextCursor());
        assertFalse(result.isHasMore());
        assertEquals("tx123", result.getContent().get(0).getId());
        assertEquals("tx456", result.getContent().get(1).getId());
        verify(queryRouter, times(1)).query(any(TransactionFilterRequest.class));
        verify(transactionMapper, times(2)).toResponse(any(Transaction.class));
    }

    @Test
    void getTransactions_WithPagination() {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .limit(1)
                .build();

        List<Transaction> transactions = Arrays.asList(transaction1);
        CursorResult<Transaction> cursorResult = CursorResult.<Transaction>builder()
                .items(transactions)
                .nextCursor("eyJzb21lIjoiY3Vyc29yIn0=")
                .build();

        when(queryRouter.query(any(TransactionFilterRequest.class))).thenReturn(cursorResult);
        when(transactionMapper.toResponse(transaction1)).thenReturn(response1);

        // When
        PageResponse<TransactionResponse> result = transactionService.getTransactions(filters);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getSize());
        assertEquals(1, result.getContent().size());
        assertNotNull(result.getNextCursor());
        assertTrue(result.isHasMore());
        assertEquals("eyJzb21lIjoiY3Vyc29yIn0=", result.getNextCursor());
        verify(queryRouter, times(1)).query(any(TransactionFilterRequest.class));
    }

    @Test
    void getTransactions_MissingUserId_ThrowsException() {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .limit(20)
                .build();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.getTransactions(filters)
        );

        assertEquals("userId is required", exception.getMessage());
        verify(queryRouter, never()).query(any(TransactionFilterRequest.class));
    }

    @Test
    void getTransactions_EmptyResults() {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .product(Product.EARNINGS)
                .limit(20)
                .build();

        CursorResult<Transaction> cursorResult = CursorResult.<Transaction>builder()
                .items(Arrays.asList())
                .nextCursor(null)
                .build();

        when(queryRouter.query(any(TransactionFilterRequest.class))).thenReturn(cursorResult);

        // When
        PageResponse<TransactionResponse> result = transactionService.getTransactions(filters);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getSize());
        assertTrue(result.getContent().isEmpty());
        assertNull(result.getNextCursor());
        assertFalse(result.isHasMore());
        verify(queryRouter, times(1)).query(any(TransactionFilterRequest.class));
    }

    @Test
    void getTransactionById_Success() {
        // Given
        String transactionId = "tx123";
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction1));
        when(transactionMapper.toResponse(transaction1)).thenReturn(response1);

        // When
        TransactionResponse result = transactionService.getTransactionById(transactionId);

        // Then
        assertNotNull(result);
        assertEquals("tx123", result.getId());
        assertEquals("user123", result.getUserId());
        assertEquals(Product.CARD, result.getProduct());
        assertEquals(TransactionType.PAYMENT, result.getType());
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        verify(transactionRepository, times(1)).findById(transactionId);
        verify(transactionMapper, times(1)).toResponse(transaction1);
    }

    @Test
    void getTransactionById_NotFound_ThrowsResourceNotFoundException() {
        // Given
        String transactionId = "non-existent-id";
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // When & Then
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> transactionService.getTransactionById(transactionId)
        );

        assertEquals("Transaction not found with id: non-existent-id", exception.getMessage());
        verify(transactionRepository, times(1)).findById(transactionId);
        verify(transactionMapper, never()).toResponse(any(Transaction.class));
    }
}
