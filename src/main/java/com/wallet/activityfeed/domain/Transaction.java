package com.wallet.activityfeed.domain;

import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Transaction entity for DynamoDB and OpenSearch
 *
 * DynamoDB Table: transactions
 *  - Partition Key (PK): user_id -> userId
 *  - Sort Key (SK): sk -> sk (format: "occurredAt#id")
 *  - GSI: id-index (PK: id)
 *
 * OpenSearch Index: activity_items
 *  - Document ID: id
 */
@DynamoDbBean
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    private String userId;
    private String sk;
    private String id;
    private Product product;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private Currency currency;
    private String description;
    private Instant occurredAt;
    private Instant createdAt;
    private String sourceService;
    private String eventId;
    private String transactionId;
    private String metadataJson;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("user_id")
    public String getUserId() {
        return userId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("sk")
    public String getSk() {
        return sk;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "id-index")
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    @DynamoDbAttribute("occurred_at")
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @DynamoDbAttribute("product")
    public Product getProduct() {
        return product;
    }

    @DynamoDbAttribute("type")
    public TransactionType getType() {
        return type;
    }

    @DynamoDbAttribute("status")
    public TransactionStatus getStatus() {
        return status;
    }

    @DynamoDbAttribute("amount")
    public BigDecimal getAmount() {
        return amount;
    }

    @DynamoDbAttribute("currency")
    public Currency getCurrency() {
        return currency;
    }

    @DynamoDbAttribute("description")
    public String getDescription() {
        return description;
    }

    @DynamoDbAttribute("created_at")
    public Instant getCreatedAt() {
        return createdAt;
    }

    @DynamoDbAttribute("source_service")
    public String getSourceService() {
        return sourceService;
    }

    @DynamoDbAttribute("event_id")
    public String getEventId() {
        return eventId;
    }

    @DynamoDbAttribute("transaction_id")
    public String getTransactionId() {
        return transactionId;
    }

    @DynamoDbAttribute("metadata")
    public String getMetadataJson() {
        return metadataJson;
    }
}
