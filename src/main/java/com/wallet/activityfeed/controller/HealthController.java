package com.wallet.activityfeed.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Health check endpoints with external dependencies")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final RestHighLevelClient openSearchClient;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @GetMapping
    @Operation(summary = "Health check", description = "Returns the health status of the service and its dependencies")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "activity-feed");
        health.put("timestamp", Instant.now());

        Map<String, String> dependencies = new HashMap<>();
        dependencies.put("dynamodb", checkDynamoDb());
        dependencies.put("sqs", checkSqs());
        dependencies.put("opensearch", checkOpenSearch());

        health.put("dependencies", dependencies);

        boolean allHealthy = dependencies.values().stream().allMatch("UP"::equals);
        health.put("status", allHealthy ? "UP" : "DEGRADED");

        return ResponseEntity.ok(health);
    }

    private String checkDynamoDb() {
        try {
            dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build());
            return "UP";
        } catch (Exception e) {
            log.error("DynamoDB health check failed", e);
            return "DOWN";
        }
    }

    private String checkSqs() {
        try {
            sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                    .build());
            return "UP";
        } catch (Exception e) {
            log.error("SQS health check failed", e);
            return "DOWN";
        }
    }

    private String checkOpenSearch() {
        try {
            openSearchClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
            return "UP";
        } catch (Exception e) {
            log.error("OpenSearch health check failed", e);
            return "DOWN";
        }
    }
}
