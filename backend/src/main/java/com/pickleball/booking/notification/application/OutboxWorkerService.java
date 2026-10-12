package com.pickleball.booking.notification.application;

import com.pickleball.booking.notification.application.OutboxEventProcessor.OutboxMessage;
import com.pickleball.booking.notification.infrastructure.WorkerQueueRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxWorkerService {
    private final WorkerQueueRepository repository;
    private final List<OutboxEventProcessor> processors;
    private final WorkerRetryPolicy retryPolicy;
    private final Instant createdAtOrAfter;
    private final Duration claimTimeout;

    public OutboxWorkerService(
            WorkerQueueRepository repository,
            List<OutboxEventProcessor> processors,
            WorkerRetryPolicy retryPolicy,
            @Value("${app.workers.outbox-created-at-or-after:1970-01-01T00:00:00Z}") Instant createdAtOrAfter,
            @Value("${app.workers.claim-timeout-ms:300000}") long claimTimeoutMillis) {
        this.repository = repository;
        this.processors = processors;
        this.retryPolicy = retryPolicy;
        this.createdAtOrAfter = createdAtOrAfter;
        this.claimTimeout = Duration.ofMillis(claimTimeoutMillis);
    }

    public int runBatch(int batchSize) {
        Set<String> supportedEventTypes = processors.stream()
                .flatMap(processor -> processor.supportedEventTypes().stream())
                .collect(Collectors.toUnmodifiableSet());
        List<OutboxMessage> messages = repository.claimOutbox(
                batchSize, supportedEventTypes, createdAtOrAfter, claimTimeout);
        for (OutboxMessage message : messages) {
            processOne(message);
        }
        return messages.size();
    }

    private void processOne(OutboxMessage message) {
        OutboxEventProcessor processor = processors.stream()
                .filter(candidate -> candidate.supports(message.eventType()))
                .findFirst()
                .orElse(null);

        if (processor == null) {
            repository.markOutboxFailed(
                    message.id(),
                    false,
                    retryPolicy.nextDelay(message.attemptCount()),
                    "OUTBOX_HANDLER_NOT_FOUND:" + message.eventType());
            return;
        }

        try {
            processor.process(message);
            repository.markOutboxProcessed(message.id());
        } catch (RuntimeException ex) {
            repository.markOutboxFailed(
                    message.id(),
                    retryPolicy.isDead(message.attemptCount()),
                    retryPolicy.nextDelay(message.attemptCount()),
                    ex.getClass().getSimpleName() + ": " + safeMessage(ex));
        }
    }

    private static String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null ? "worker processing failed" : ex.getMessage();
    }
}
