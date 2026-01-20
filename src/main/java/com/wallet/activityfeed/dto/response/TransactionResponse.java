package com.wallet.activityfeed.dto.response;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private String id;
    private String userId;
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
    private Map<String, Object> metadata;
}
