package com.wallet.activityfeed.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbTransactionRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DynamoDbTable<Transaction> dynamoDbTable;

    @Mock
    private DynamoDbIndex<Transaction> dynamoDbIndex;

    @Mock
    private PageIterable<Transaction> pageIterable;

    @Mock
    private Page<Transaction> page;

    @InjectMocks
    private DynamoDbTransactionRepository repository;

    private Transaction transaction1;
    private Transaction transaction2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repository, "tableName", "transactions");

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
                .metadataJson("{\"merchantName\":\"Starbucks\"}")
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
                .metadataJson("{\"cryptoSymbol\":\"BTC\"}")
                .occurredAt(now.plusSeconds(60))
                .createdAt(now.plusSeconds(60))
                .build();

        lenient().when(dynamoDbEnhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(dynamoDbTable);
    }

    @Test
    void save_Success() {
        // Given
        doNothing().when(dynamoDbTable).putItem(any(PutItemEnhancedRequest.class));

        // When
        Transaction result = repository.save(transaction1);

        // Then
        assertNotNull(result);
        assertEquals("tx123", result.getId());
        assertEquals("user123", result.getUserId());
        verify(dynamoDbTable, times(1)).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    void save_DuplicateTransaction_ThrowsException() {
        // Given
        doThrow(ConditionalCheckFailedException.builder().build())
                .when(dynamoDbTable).putItem(any(PutItemEnhancedRequest.class));

        // When & Then
        assertThrows(
                ConditionalCheckFailedException.class,
                () -> repository.save(transaction1)
        );
        verify(dynamoDbTable, times(1)).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    void findById_Success() {
        // Given
        Iterator<Page<Transaction>> iterator = Arrays.asList(page).iterator();
        when(dynamoDbTable.index("id-index")).thenReturn(dynamoDbIndex);
        when(dynamoDbIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);
        when(page.items()).thenReturn(Arrays.asList(transaction1));

        // When
        Optional<Transaction> result = repository.findById("tx123");

        // Then
        assertTrue(result.isPresent());
        assertEquals("tx123", result.get().getId());
        assertEquals("user123", result.get().getUserId());
        verify(dynamoDbTable, times(1)).index("id-index");
        verify(dynamoDbIndex, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findById_NotFound() {
        // Given
        Iterator<Page<Transaction>> iterator = Collections.emptyIterator();
        when(dynamoDbTable.index("id-index")).thenReturn(dynamoDbIndex);
        when(dynamoDbIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);

        // When
        Optional<Transaction> result = repository.findById("non-existent-id");

        // Then
        assertFalse(result.isPresent());
        verify(dynamoDbTable, times(1)).index("id-index");
        verify(dynamoDbIndex, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findById_EmptyPage() {
        // Given
        Iterator<Page<Transaction>> iterator = Arrays.asList(page).iterator();
        when(dynamoDbTable.index("id-index")).thenReturn(dynamoDbIndex);
        when(dynamoDbIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);
        when(page.items()).thenReturn(Collections.emptyList());

        // When
        Optional<Transaction> result = repository.findById("tx123");

        // Then
        assertFalse(result.isPresent());
        verify(dynamoDbTable, times(1)).index("id-index");
        verify(dynamoDbIndex, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findByUserId_Success() {
        // Given
        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        Iterator<Page<Transaction>> iterator = Arrays.asList(page).iterator();

        when(dynamoDbTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);
        when(page.items()).thenReturn(transactions);

        // When
        CursorResult<Transaction> result = repository.findByUserId("user123", null, 10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertNull(result.getNextCursor());
        assertEquals("tx123", result.getItems().get(0).getId());
        assertEquals("tx456", result.getItems().get(1).getId());
        verify(dynamoDbTable, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findByUserId_WithPagination() throws Exception {
        // Given
        List<Transaction> transactions = Arrays.asList(transaction1, transaction2, transaction1);
        Iterator<Page<Transaction>> iterator = Arrays.asList(page).iterator();

        when(dynamoDbTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);
        when(page.items()).thenReturn(transactions);
        when(objectMapper.writeValueAsString(any())).thenReturn("encoded-cursor");

        // When
        CursorResult<Transaction> result = repository.findByUserId("user123", null, 2);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertNotNull(result.getNextCursor());
        verify(dynamoDbTable, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findByUserId_EmptyResults() {
        // Given
        Iterator<Page<Transaction>> emptyIterator = Collections.emptyIterator();

        when(dynamoDbTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(emptyIterator);

        // When
        CursorResult<Transaction> result = repository.findByUserId("user123", null, 10);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertNull(result.getNextCursor());
        verify(dynamoDbTable, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findByUserIdAndDateRange_Success() {
        // Given
        List<Transaction> transactions = Arrays.asList(transaction1, transaction2);
        Iterator<Page<Transaction>> iterator = Arrays.asList(page).iterator();

        when(dynamoDbTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(iterator);
        when(page.items()).thenReturn(transactions);

        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        // When
        CursorResult<Transaction> result = repository.findByUserIdAndDateRange(
                "user123", startDate, endDate, null, 10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertNull(result.getNextCursor());
        verify(dynamoDbTable, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findByUserIdAndDateRange_EmptyResults() {
        // Given
        Iterator<Page<Transaction>> emptyIterator = Collections.emptyIterator();

        when(dynamoDbTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.iterator()).thenReturn(emptyIterator);

        Instant startDate = Instant.now().minusSeconds(7200);
        Instant endDate = Instant.now().minusSeconds(3600);

        // When
        CursorResult<Transaction> result = repository.findByUserIdAndDateRange(
                "user123", startDate, endDate, null, 10);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertNull(result.getNextCursor());
        verify(dynamoDbTable, times(1)).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void deleteById_ThrowsUnsupportedOperationException() {
        // When & Then
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> repository.deleteById("tx123")
        );

        assertTrue(exception.getMessage().contains("not supported"));
        verifyNoInteractions(dynamoDbTable);
    }
}
