package com.wallet.activityfeed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * AWS SDK configuration for DynamoDB and SQS
 * Configured for LocalStack in development
 */
@Configuration
public class AwsConfig {

    @Value("${aws.endpoint:http://localhost:4566}")
    private String awsEndpoint;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.access-key-id:test}")
    private String awsAccessKeyId;

    @Value("${aws.secret-access-key:test}")
    private String awsSecretAccessKey;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(awsEndpoint))
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                ))
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(URI.create(awsEndpoint))
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                ))
                .build();
    }
}
