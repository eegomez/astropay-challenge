package com.wallet.activityfeed.event;

import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Standard event format published by source microservices to SQS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    private String eventId;
    private String eventType;
    private String sourceService;
    private Instant eventTimestamp;

    private TransactionEventPayload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionEventPayload {
        private String transactionId;
        private String userId;
        private Product product;
        private TransactionType type;
        private TransactionStatus status;
        private BigDecimal amount;
        private Currency currency;
        private String description;
        private Instant occurredAt;
        private Map<String, Object> metadata;
    }
}
