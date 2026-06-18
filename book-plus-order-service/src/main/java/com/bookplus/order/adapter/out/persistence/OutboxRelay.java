package com.bookplus.order.adapter.out.persistence;

import com.bookplus.order.adapter.out.persistence.entity.OutboxEventEntity;
import com.bookplus.order.adapter.out.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Outbox Relay — polls PENDING outbox rows and publishes them to Kafka.
 *
 * Guarantees:
 *  - Events are published AT LEAST ONCE (idempotent consumers handle deduplication).
 *  - If Kafka is down, events remain PENDING and are retried on the next tick.
 *  - After MAX_RETRIES failures the event is marked FAILED for manual inspection.
 *  - Published events are cleaned up after 7 days to keep the table lean.
 *
 * Runs every 5 seconds by default (configurable via order.outbox.poll-ms).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE  = 50;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventJpaRepository       repository;
    private final KafkaTemplate<String, String>  kafkaTemplate;

    @Scheduled(fixedDelayString = "${order.outbox.poll-ms:5000}")
    @SchedulerLock(name = "outboxRelay", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pending = repository.findByStatusOrderByCreatedAtAsc(
                "PENDING", PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;

        log.debug("Outbox relay: processing {} pending events", pending.size());

        for (OutboxEventEntity event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get();    // block to confirm broker ack before marking PUBLISHED

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                log.debug("Outbox published: {} → topic={}", event.getEventType(), event.getTopic());

            } catch (Exception ex) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                event.setLastError(ex.getMessage());

                if (retries >= MAX_RETRIES) {
                    event.setStatus("FAILED");
                    log.error("Outbox event FAILED after {} retries: {} aggregateId={}",
                            MAX_RETRIES, event.getEventType(), event.getAggregateId());
                } else {
                    log.warn("Outbox publish failed (attempt {}/{}): {} — {}",
                            retries, MAX_RETRIES, event.getEventType(), ex.getMessage());
                }
            }
            repository.save(event);
        }
    }

    /** Clean up old PUBLISHED events once per hour */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanupPublishedEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = repository.deletePublishedBefore(cutoff);
        if (deleted > 0) log.info("Outbox cleanup: deleted {} published events older than 7 days", deleted);
    }
}
