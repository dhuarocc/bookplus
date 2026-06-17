package com.bookplus.order.adapter.out.persistence;

import com.bookplus.order.adapter.out.persistence.entity.OutboxEventEntity;
import com.bookplus.order.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.bookplus.order.domain.event.*;
import com.bookplus.order.domain.model.DomainEvent;
import com.bookplus.order.domain.port.out.OutboxEventPublisherPort;
import com.bookplus.order.shared.annotation.PersistenceAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Writes domain events to the outbox table.
 * Called within the same @Transactional boundary as the aggregate save —
 * either both the aggregate and the event are persisted, or neither is.
 */
@PersistenceAdapter
@RequiredArgsConstructor
@Slf4j
public class OutboxEventAdapter implements OutboxEventPublisherPort {

    private final OutboxEventJpaRepository repository;
    private final ObjectMapper             objectMapper;

    private static final Map<Class<?>, String> TOPIC_MAP = Map.of(
            OrderCreatedEvent.class,            "order.created",
            OrderCancelledEvent.class,          "order.cancelled",
            OrderStatusChangedEvent.class,      "order.status.changed",
            OrderPaymentConfirmedEvent.class,   "order.payment.confirmed",
            OrderRefundedEvent.class,           "order.refunded",
            DigitalAccessRevokedEvent.class,    "order.access.revoked"
    );

    @Override
    public void saveAll(String aggregateType, List<DomainEvent> events) {
        for (DomainEvent event : events) {
            String topic = TOPIC_MAP.get(event.getClass());
            if (topic == null) {
                log.warn("No topic mapping for outbox event type: {}", event.getClass().getSimpleName());
                continue;
            }
            try {
                String payload = objectMapper.writeValueAsString(event);
                repository.save(OutboxEventEntity.builder()
                        .aggregateType(aggregateType)
                        .aggregateId(event.aggregateId())
                        .eventType(event.getClass().getSimpleName())
                        .topic(topic)
                        .partitionKey(event.aggregateId())
                        .payload(payload)
                        .build());
            } catch (Exception ex) {
                // This runs inside a DB transaction — throwing here will roll back both
                // the aggregate save AND the outbox write, which is exactly what we want.
                throw new RuntimeException("Failed to write event to outbox: " + event.getClass().getSimpleName(), ex);
            }
        }
    }
}
