package com.wallet.activityfeed.dto.request;

import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CreateTransactionRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Product is required")
    private Product product;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @NotNull(message = "Status is required")
    private TransactionStatus status;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Currency currency;

    private String description;

    private Instant occurredAt;

    @NotBlank(message = "Source service is required")
    private String sourceService;

    private String eventId;
    private String transactionId;

    private Map<String, Object> metadata;
}
