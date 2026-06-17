package com.bookplus.inventory.domain.model;

import com.bookplus.inventory.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StockReservation (entity)")
class StockReservationTest {

    private BookId book() { return BookId.of(UUID.randomUUID().toString()); }

    private StockReservation pending() {
        return StockReservation.create(book(), "ORD-1", "user-1", 5);
    }

    @Test
    @DisplayName("create() arranca PENDING, activa, con TTL futuro")
    void create_isPendingAndActive() {
        StockReservation r = pending();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(r.isPending()).isTrue();
        assertThat(r.isActive()).isTrue();
        assertThat(r.isExpired()).isFalse();
        assertThat(r.getQuantity()).isEqualTo(5);
        assertThat(r.getExpiresAt()).isAfter(r.getCreatedAt());
    }

    @Test
    @DisplayName("confirm() pasa a CONFIRMED; confirmar de nuevo lanza DomainException")
    void confirm_thenAgain_throws() {
        StockReservation r = pending();
        r.confirm();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.getResolvedAt()).isNotNull();
        assertThatThrownBy(r::confirm).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("cancel() libera; no se puede cancelar una confirmada ni una ya cancelada")
    void cancel_rules() {
        StockReservation r = pending();
        r.cancel();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThatThrownBy(r::cancel).isInstanceOf(DomainException.class).hasMessageContaining("already cancelled");

        StockReservation confirmed = pending();
        confirmed.confirm();
        assertThatThrownBy(confirmed::cancel).isInstanceOf(DomainException.class).hasMessageContaining("confirmed");
    }

    @Test
    @DisplayName("expire() solo desde PENDING")
    void expire_rules() {
        StockReservation r = pending();
        r.expire();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(r.isExpired()).isTrue();
        assertThatThrownBy(r::expire).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("cantidad no positiva lanza DomainException")
    void invalidQuantity_throws() {
        assertThatThrownBy(() -> StockReservation.create(book(), "ORD-1", "user-1", 0))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StockReservation.create(book(), "ORD-1", "user-1", -2))
                .isInstanceOf(DomainException.class);
    }
}
