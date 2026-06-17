package com.bookplus.cart.domain.model;

import com.bookplus.cart.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money (cart value object)")
class MoneyTest {

    @Test
    @DisplayName("of()/zero() normalizan a 2 decimales")
    void scaling() {
        assertThat(Money.of(new BigDecimal("9.1"), "USD").amount()).isEqualByComparingTo("9.10");
        assertThat(Money.zero("USD").amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("multiply() multiplica el importe por un factor")
    void multiply() {
        assertThat(Money.of(new BigDecimal("3.50"), "USD").multiply(3).amount()).isEqualByComparingTo("10.50");
    }

    @Test
    @DisplayName("add() suma importes de la misma moneda")
    void add_sameCurrency() {
        Money r = Money.of(new BigDecimal("3.00"), "USD").add(Money.of(new BigDecimal("4.50"), "USD"));
        assertThat(r.amount()).isEqualByComparingTo("7.50");
    }

    @Test
    @DisplayName("add() con monedas distintas lanza DomainException")
    void add_mismatch_throws() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "USD").add(Money.of(BigDecimal.ONE, "PEN")))
                .isInstanceOf(DomainException.class).hasMessageContaining("different currencies");
    }

    @Test
    @DisplayName("importe negativo lanza DomainException; nulls lanzan NPE")
    void invalid() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1"), "USD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Money.of(null, "USD")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }
}
