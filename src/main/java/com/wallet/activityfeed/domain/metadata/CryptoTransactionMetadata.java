package com.wallet.activityfeed.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTransactionMetadata {
    private String cryptoCurrency;
    private String walletAddress;
    private String transactionHash;
    private String network;
    private String exchangeRate;
    private Integer confirmations;
}
