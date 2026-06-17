package com.bookplus.order.adapter.out.messaging;

import com.bookplus.order.domain.event.*;
import com.bookplus.order.domain.model.DomainEvent;
import com.bookplus.order.domain.port.out.DomainEventPublisherPort;
import com.bookplus.order.shared.annotation.PersistenceAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

@PersistenceAdapter
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisherAdapter implements DomainEventPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    private static final Map<Class<?>, String> TOPICS = Map.of(
            OrderCreatedEvent.class,         "order.created",
            OrderCancelledEvent.class,       "order.cancelled",
            OrderStatusChangedEvent.class,   "order.status.changed",
            DigitalAccessRevokedEvent.class, "order.access.revoked"
    );

    @Override
    public void publishAll(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            String topic = TOPICS.get(event.getClass());
            if (topic == null) {
                log.warn("No topic for event type {}", event.getClass().getSimpleName());
                continue;
            }
            try {
                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(topic, event.aggregateId(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null)
                                log.error("Failed to send {} to {}: {}", event.getClass().getSimpleName(), topic, ex.getMessage());
                            else
                                log.debug("Published {} to {} offset {}", event.getClass().getSimpleName(), topic,
                                        result.getRecordMetadata().offset());
                        });
            } catch (Exception ex) {
                log.error("Serialization error for {}: {}", event.getClass().getSimpleName(), ex.getMessage());
                throw new RuntimeException("Failed to publish domain event", ex);
            }
        }
    }
}
