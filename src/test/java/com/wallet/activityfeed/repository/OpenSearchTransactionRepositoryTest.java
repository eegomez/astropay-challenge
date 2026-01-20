package com.wallet.activityfeed.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.domain.CursorResult;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import com.wallet.activityfeed.dto.request.TransactionFilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenSearchTransactionRepositoryTest {

    @Mock
    private RestHighLevelClient openSearchClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SearchResponse searchResponse;

    @Mock
    private SearchHits searchHits;

    @Mock
    private IndexResponse indexResponse;

    @InjectMocks
    private OpenSearchTransactionRepository repository;

    private Transaction transaction1;
    private Transaction transaction2;
    private Map<String, Object> transactionMap;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(repository, "indexName", "activity_items");

        Instant now = Instant.now();
        String sk1 = now.toString() + "#tx123";

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
                .sk(now.plusSeconds(60).toString() + "#tx456")
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

        transactionMap = new HashMap<>();
        transactionMap.put("id", "tx123");
        transactionMap.put("userId", "user123");
        transactionMap.put("product", "CARD");
        transactionMap.put("type", "PAYMENT");
        transactionMap.put("status", "COMPLETED");
        transactionMap.put("amount", 50.00);
        transactionMap.put("currency", "USD");
        transactionMap.put("description", "Coffee at Starbucks");
        transactionMap.put("metadataJson", "{\"merchantName\":\"Starbucks\"}");
    }

    @Test
    void index_Success() throws Exception {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("merchantName", "Starbucks");

        when(objectMapper.convertValue(any(Transaction.class), eq(Map.class))).thenReturn(transactionMap);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(metadata);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"metadata\":{\"merchantName\":\"Starbucks\"}}");
        when(openSearchClient.index(any(IndexRequest.class), any(RequestOptions.class))).thenReturn(indexResponse);

        // When
        repository.index(transaction1);

        // Then
        verify(openSearchClient, times(1)).index(any(IndexRequest.class), any(RequestOptions.class));
        verify(objectMapper, times(1)).convertValue(any(Transaction.class), eq(Map.class));
    }

    @Test
    void index_ThrowsException() throws Exception {
        // Given
        when(objectMapper.convertValue(any(Transaction.class), eq(Map.class))).thenReturn(transactionMap);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new HashMap<>());
        doThrow(JsonProcessingException.class)
                .when(objectMapper).writeValueAsString(any());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> repository.index(transaction1)
        );

        assertTrue(exception.getMessage().contains("Failed to index transaction"));
        verify(openSearchClient, never()).index(any(IndexRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_Success() throws Exception {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .limit(10)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        SearchHit hit1 = mock(SearchHit.class);
        SearchHit hit2 = mock(SearchHit.class);
        SearchHit[] hitsArray = new SearchHit[]{hit1, hit2};

        Map<String, Object> source1 = new HashMap<>();
        source1.put("id", "tx123");
        source1.put("userId", "user123");
        source1.put("product", "CARD");
        source1.put("type", "PAYMENT");
        source1.put("status", "COMPLETED");
        source1.put("amount", 50.00);
        source1.put("currency", "USD");
        source1.put("description", "Coffee");

        Map<String, Object> source2 = new HashMap<>();
        source2.put("id", "tx456");
        source2.put("userId", "user123");
        source2.put("product", "CRYPTO");
        source2.put("type", "DEPOSIT");
        source2.put("status", "COMPLETED");
        source2.put("amount", 0.5);
        source2.put("currency", "USD");
        source2.put("description", "BTC deposit");

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(searchResponse);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getHits()).thenReturn(hitsArray);
        when(hit1.getSourceAsMap()).thenReturn(source1);
        when(hit2.getSourceAsMap()).thenReturn(source2);
        when(objectMapper.convertValue(any(Map.class), eq(Transaction.class)))
                .thenReturn(transaction1, transaction2);

        // When
        CursorResult<Transaction> result = repository.search(filters);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertNull(result.getNextCursor());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_WithPagination() throws Exception {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .limit(2)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        SearchHit hit1 = mock(SearchHit.class);
        SearchHit hit2 = mock(SearchHit.class);
        SearchHit hit3 = mock(SearchHit.class);
        SearchHit[] hitsArray = new SearchHit[]{hit1, hit2, hit3};

        Map<String, Object> source = new HashMap<>();
        source.put("id", "tx123");
        source.put("userId", "user123");

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(searchResponse);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getHits()).thenReturn(hitsArray);
        when(hit1.getSourceAsMap()).thenReturn(source);
        when(hit2.getSourceAsMap()).thenReturn(source);
        when(hit3.getSourceAsMap()).thenReturn(source);
        when(hit2.getSortValues()).thenReturn(new Object[]{"2024-01-02", "tx2"});
        when(objectMapper.convertValue(any(Map.class), eq(Transaction.class))).thenReturn(transaction1);
        when(objectMapper.writeValueAsString(any())).thenReturn("encoded-cursor");

        // When
        CursorResult<Transaction> result = repository.search(filters);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertNotNull(result.getNextCursor());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_WithFilters() throws Exception {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .product(Product.CARD)
                .status(TransactionStatus.COMPLETED)
                .currency(Currency.USD)
                .limit(10)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        SearchHit[] hitsArray = new SearchHit[0];

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(searchResponse);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getHits()).thenReturn(hitsArray);

        // When
        CursorResult<Transaction> result = repository.search(filters);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertNull(result.getNextCursor());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_WithMetadataFilter() throws Exception {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .metadataField("merchantName")
                .metadataValue("Starbucks")
                .limit(10)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        SearchHit hit = mock(SearchHit.class);
        SearchHit[] hitsArray = new SearchHit[]{hit};
        Map<String, Object> source = new HashMap<>();
        source.put("id", "tx123");
        source.put("userId", "user123");

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(searchResponse);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getHits()).thenReturn(hitsArray);
        when(hit.getSourceAsMap()).thenReturn(source);
        when(objectMapper.convertValue(any(Map.class), eq(Transaction.class))).thenReturn(transaction1);

        // When
        CursorResult<Transaction> result = repository.search(filters);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_ThrowsException() throws Exception {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .limit(10)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class)))
                .thenThrow(new IOException("Search error"));

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> repository.search(filters)
        );

        assertTrue(exception.getMessage().contains("Failed to search transactions"));
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_WithSearchText() throws Exception {
        // Given
        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .searchText("Starbucks")
                .limit(10)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        SearchHit[] hitsArray = new SearchHit[0];

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(searchResponse);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getHits()).thenReturn(hitsArray);

        // When
        CursorResult<Transaction> result = repository.search(filters);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void search_WithDateRange() throws Exception {
        // Given
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        TransactionFilterRequest filters = TransactionFilterRequest.builder()
                .userId("user123")
                .startDate(startDate)
                .endDate(endDate)
                .limit(10)
                .sortBy("occurredAt")
                .sortDirection("DESC")
                .build();

        SearchHit[] hitsArray = new SearchHit[0];

        when(openSearchClient.search(any(SearchRequest.class), any(RequestOptions.class))).thenReturn(searchResponse);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(searchHits.getHits()).thenReturn(hitsArray);

        // When
        CursorResult<Transaction> result = repository.search(filters);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), any(RequestOptions.class));
    }
}
