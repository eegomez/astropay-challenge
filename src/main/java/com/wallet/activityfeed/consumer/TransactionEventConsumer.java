package com.wallet.activityfeed.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.activityfeed.event.TransactionEvent;
import com.wallet.activityfeed.usecase.TransactionEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQS consumer that continuously polls for transaction events from other microservices
 * Implements SmartLifecycle for proper startup/shutdown management
 * Uses ThreadPool for parallel message processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer implements SmartLifecycle {

    private final SqsClient sqsClient;
    private final TransactionEventProcessor eventProcessor;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${aws.sqs.worker-threads:5}")
    private int workerThreads;

    @Value("${aws.sqs.queue-capacity:20}")
    private int queueCapacity;

    @Value("${aws.sqs.visibility-timeout:30}")
    private int visibilityTimeout;

    private static final int MAX_MESSAGES = 10;
    private static final int WAIT_TIME_SECONDS = 10; // Long polling (balanced for quick shutdown)
    private static final long CONSUMER_THREAD_JOIN_TIMEOUT_MS = 5000;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final long ERROR_BACKOFF_SLEEP_MS = 1000;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread consumerThread;
    private volatile ThreadPoolExecutor executorService;

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting SQS consumer for queue: {} with {} worker threads, queue capacity: {}, visibility timeout: {}s",
                    queueUrl, workerThreads, queueCapacity, visibilityTimeout);

            // ThreadPoolExecutor with bounded queue and CallerRunsPolicy for backpressure
            executorService = new ThreadPoolExecutor(
                    workerThreads,
                    workerThreads,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            consumerThread = new Thread(this::consumeMessages, "sqs-consumer-thread");
            consumerThread.setDaemon(false);
            consumerThread.start();
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping SQS consumer...");

            // Stop accepting new tasks
            if (executorService != null) {
                executorService.shutdown();
            }

            // Interrupt the consumer thread
            if (consumerThread != null) {
                consumerThread.interrupt();
                try {
                    consumerThread.join(CONSUMER_THREAD_JOIN_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for consumer thread to stop");
                }
            }

            // Wait for worker threads to finish
            if (executorService != null) {
                try {
                    if (!executorService.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        log.warn("Worker threads did not finish in time, forcing shutdown");
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executorService.shutdownNow();
                }
            }

            log.info("SQS consumer stopped");
        }
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * Continuous message consumption loop
     * Uses long polling to efficiently wait for messages
     */
    private void consumeMessages() {
        log.info("SQS consumer thread started and listening for messages...");

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Long polling with visibility timeout
                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(MAX_MESSAGES)
                        .waitTimeSeconds(WAIT_TIME_SECONDS)
                        .visibilityTimeout(visibilityTimeout)  // Prevent redelivery during processing
                        .build();

                List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

                if (!messages.isEmpty()) {
                    log.info("Received {} messages from SQS queue", messages.size());

                    // Submit messages to thread pool for parallel processing
                    for (Message message : messages) {
                        if (!running.get()) {
                            log.info("Consumer stopping, not submitting remaining messages");
                            break;
                        }

                        // Check if executor is shutdown before submitting (avoid RejectedExecutionException)
                        if (executorService.isShutdown()) {
                            log.info("ExecutorService is shutdown, stopping message submission");
                            break;
                        }

                        try {
                            executorService.submit(() -> {
                                try {
                                    processMessage(message);
                                } catch (Exception e) {
                                    log.error("Error processing SQS message: {}", message.messageId(), e);
                                    // Message will not be deleted and will be retried or go to DLQ
                                }
                            });
                        } catch (RejectedExecutionException e) {
                            // Executor is shutdown or queue is full (shouldn't happen with CallerRunsPolicy)
                            log.warn("Message rejected by executor (shutdown in progress): {}", message.messageId());
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error polling SQS queue", e);
                    // Brief sleep before retrying to avoid tight loop on persistent errors
                    try {
                        Thread.sleep(ERROR_BACKOFF_SLEEP_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.info("Consumer thread interrupted during error backoff, shutting down");
                        break;
                    }
                }
            }
        }

        log.info("SQS consumer thread stopped");
    }

    private void processMessage(Message message) throws Exception {
        log.debug("Processing message: {}", message.messageId());

        // Parse event from message body
        TransactionEvent event = objectMapper.readValue(message.body(), TransactionEvent.class);

        // Process event (store in DynamoDB and OpenSearch)
        eventProcessor.processEvent(event);

        // Delete message from queue after successful processing
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
        log.debug("Successfully processed and deleted message: {}", message.messageId());
    }
}
