package com.wallet.activityfeed.dto.request;

import com.wallet.activityfeed.domain.enums.Currency;
import com.wallet.activityfeed.domain.enums.Product;
import com.wallet.activityfeed.domain.enums.TransactionStatus;
import com.wallet.activityfeed.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilterRequest {
    private String userId;
    private Product product;
    private TransactionType type;
    private TransactionStatus status;
    private Currency currency;
    private Instant startDate;
    private Instant endDate;
    private String searchText;

    // For searching specific metadata fields (e.g., walletAddress for crypto)
    private String metadataField;
    private String metadataValue;

    // Cursor-based pagination
    private String cursor; // Base64 encoded pagination token
    private Integer limit = 20; // Number of results per page
    private String sortBy = "occurredAt";
    private String sortDirection = "DESC";
}
