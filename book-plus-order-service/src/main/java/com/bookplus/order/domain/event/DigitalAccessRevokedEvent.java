package com.bookplus.order.domain.event;

import com.bookplus.order.domain.model.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Se publica cuando se reembolsa un pedido DIGITAL. Consumido por catalog-service,
 * que revoca el acceso del usuario a esos libros en su biblioteca (a diferencia del
 * físico, donde no hay acceso digital que retirar). Así, devolver el dinero no deja
 * al usuario con el libro: pérdida cero.
 */
@Getter
public class DigitalAccessRevokedEvent implements DomainEvent {

    private final String     orderId;
    private final String     userId;
    private final List<Item> items;
    private final Instant    occurredOn;

    public record Item(String bookId) {}

    public DigitalAccessRevokedEvent(String orderId, String userId, List<Item> items) {
        this.orderId    = orderId;
        this.userId     = userId;
        this.items      = List.copyOf(items);
        this.occurredOn = Instant.now();
    }

    @Override public String  aggregateId() { return orderId; }
    @Override public Instant occurredOn()  { return occurredOn; }
}
