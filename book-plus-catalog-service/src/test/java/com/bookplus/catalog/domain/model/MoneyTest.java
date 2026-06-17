package com.bookplus.catalog.domain.model;

import com.bookplus.catalog.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money (value object)")
class MoneyTest {

    @Test
    @DisplayName("of() normaliza a 2 decimales (HALF_UP)")
    void of_scalesToTwoDecimals() {
        assertThat(Money.of(new BigDecimal("9.999"), "USD").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of(new BigDecimal("5"), "USD").amount()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("constructores de conveniencia fijan la moneda")
    void convenienceConstructors() {
        assertThat(Money.ofUSD(new BigDecimal("3.50")).currency()).isEqualTo("USD");
        assertThat(Money.ofPEN(new BigDecimal("3.50")).currency()).isEqualTo("PEN");
        assertThat(Money.zero("USD").amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("importe negativo lanza DomainException")
    void negative_throws() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1.00"), "USD"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("amount/currency null lanza NPE")
    void nulls_throw() {
        assertThatThrownBy(() -> Money.of(null, "USD")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("isGreaterThan y subtract operan con la misma moneda")
    void arithmetic_sameCurrency() {
        Money ten = Money.ofUSD(new BigDecimal("10.00"));
        Money four = Money.ofUSD(new BigDecimal("4.00"));
        assertThat(ten.isGreaterThan(four)).isTrue();
        assertThat(four.isGreaterThan(ten)).isFalse();
        assertThat(ten.subtract(four).amount()).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("operar con monedas distintas lanza DomainException")
    void currencyMismatch_throws() {
        Money usd = Money.ofUSD(new BigDecimal("10.00"));
        Money pen = Money.ofPEN(new BigDecimal("10.00"));
        assertThatThrownBy(() -> usd.subtract(pen)).isInstanceOf(DomainException.class).hasMessageContaining("mismatch");
        assertThatThrownBy(() -> usd.isGreaterThan(pen)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("toString muestra importe y moneda; equals por valor")
    void toString_andEquals() {
        assertThat(Money.ofUSD(new BigDecimal("12.5")).toString()).isEqualTo("12.50 USD");
        assertThat(Money.ofUSD(new BigDecimal("12.50"))).isEqualTo(Money.ofUSD(new BigDecimal("12.5")));
    }
}
