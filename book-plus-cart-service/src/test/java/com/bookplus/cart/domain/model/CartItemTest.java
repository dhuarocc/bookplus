package com.bookplus.cart.domain.model;

import com.bookplus.cart.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CartItem (entity)")
class CartItemTest {

    private BookId book() { return BookId.of(UUID.randomUUID().toString()); }
    private Money price(String v) { return Money.of(new BigDecimal(v), "USD"); }

    @Test
    @DisplayName("create() fija los datos y calcula el subtotal")
    void create_andSubtotal() {
        CartItem it = CartItem.create(book(), "Clean Code", null, "ISBN-1", 3, price("29.99"));
        assertThat(it.getQuantity()).isEqualTo(3);
        assertThat(it.getTitle()).isEqualTo("Clean Code");
        assertThat(it.subtotal().amount()).isEqualByComparingTo("89.97");
    }

    @Test
    @DisplayName("increaseQuantity() suma; setQuantity() reemplaza")
    void quantityChanges() {
        CartItem it = CartItem.create(book(), "T", null, "I", 2, price("10.00"));
        it.increaseQuantity(3);
        assertThat(it.getQuantity()).isEqualTo(5);
        it.setQuantity(1);
        assertThat(it.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("cantidad fuera de 1..99 lanza DomainException")
    void invalidQuantity_throws() {
        assertThatThrownBy(() -> CartItem.create(book(), "T", null, "I", 0, price("10.00")))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CartItem.create(book(), "T", null, "I", 100, price("10.00")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("syncPrice() actualiza el precio unitario")
    void syncPrice() {
        CartItem it = CartItem.create(book(), "T", null, "I", 1, price("10.00"));
        it.syncPrice(price("12.50"));
        assertThat(it.getUnitPrice().amount()).isEqualByComparingTo("12.50");
        assertThat(it.subtotal().amount()).isEqualByComparingTo("12.50");
    }
}
