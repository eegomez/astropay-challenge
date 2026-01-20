package com.wallet.activityfeed.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import com.wallet.activityfeed.domain.Transaction;
import com.wallet.activityfeed.dto.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionMapper {

    private final ObjectMapper objectMapper;

    public TransactionResponse toResponse(Transaction transaction) {
        Map<String, Object> metadata = null;
        if (transaction.getMetadataJson() != null && !transaction.getMetadataJson().isEmpty()) {
            try {
                metadata = objectMapper.readValue(
                        transaction.getMetadataJson(),
                        new TypeReference<>() {
                        }
                );
            } catch (IOException e) {
                log.error("Failed to deserialize metadata for transaction: {}", transaction.getId(), e);
            }
        }

        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .product(transaction.getProduct())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .description(transaction.getDescription())
                .occurredAt(transaction.getOccurredAt())
                .createdAt(transaction.getCreatedAt())
                .sourceService(transaction.getSourceService())
                .eventId(transaction.getEventId())
                .transactionId(transaction.getTransactionId())
                .metadata(metadata)
                .build();
    }
}
