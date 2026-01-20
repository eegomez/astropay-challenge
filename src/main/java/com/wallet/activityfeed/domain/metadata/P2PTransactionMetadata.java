package com.wallet.activityfeed.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class P2PTransactionMetadata {
    private String peerUserId;
    private String peerName;
    private String peerEmail;
    private String message;
    private Boolean isRequest;
}
