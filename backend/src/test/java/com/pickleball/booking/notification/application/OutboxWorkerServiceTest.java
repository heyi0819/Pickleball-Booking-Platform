package com.pickleball.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.booking.notification.infrastructure.WorkerQueueRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OutboxWorkerServiceTest {
    private final WorkerQueueRepository repository = mock(WorkerQueueRepository.class);
    private final WorkerRetryPolicy retryPolicy = new WorkerRetryPolicy();

    @Test
    void claimsOnlyRegisteredEventTypesAtOrAfterTheActivationCutoff() {
        Instant cutoff = Instant.parse("2026-10-08T08:53:00Z");
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        when(processor.supportedEventTypes()).thenReturn(Set.of("COURSE_CREATED_FROM_OFFERING"));
        Duration claimTimeout = Duration.ofMinutes(5);
        when(repository.claimOutbox(50, Set.of("COURSE_CREATED_FROM_OFFERING"), cutoff, claimTimeout))
                .thenReturn(List.of());
        var service = new OutboxWorkerService(
                repository, List.of(processor), retryPolicy, cutoff, claimTimeout.toMillis());

        assertThat(service.runBatch(50)).isZero();

        verify(repository).claimOutbox(
                50, Set.of("COURSE_CREATED_FROM_OFFERING"), cutoff, claimTimeout);
    }
}
