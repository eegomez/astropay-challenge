package com.wallet.activityfeed.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsTransactionMetadata {
    private String earningType;
    private String source;
    private String description;
    private String referenceId;
}
