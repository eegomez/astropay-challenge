package com.wallet.activityfeed.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTransactionMetadata {
    private String merchantName;
    private String merchantCategory;
    private String cardLast4Digits;
    private String cardType;
    private String location;
}
