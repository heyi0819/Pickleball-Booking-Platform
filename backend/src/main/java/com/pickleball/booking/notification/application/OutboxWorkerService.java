package com.pickleball.booking.notification.application;

import com.pickleball.booking.notification.application.OutboxEventProcessor.OutboxMessage;
import com.pickleball.booking.notification.infrastructure.WorkerQueueRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OutboxWorkerService {
    private final WorkerQueueRepository repository;
    private final List<OutboxEventProcessor> processors;
    private final WorkerRetryPolicy retryPolicy;

    public OutboxWorkerService(
            WorkerQueueRepository repository,
            List<OutboxEventProcessor> processors,
            WorkerRetryPolicy retryPolicy) {
        this.repository = repository;
        this.processors = processors;
        this.retryPolicy = retryPolicy;
    }

    public int runBatch(int batchSize) {
        List<OutboxMessage> messages = repository.claimOutbox(batchSize);
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
