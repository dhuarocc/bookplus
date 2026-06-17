package com.bookplus.catalog.domain.model;

import com.bookplus.catalog.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Rating (value object, 1-5)")
class RatingTest {

    @Test
    @DisplayName("acepta valores de 1 a 5")
    void validRange() {
        for (int v = 1; v <= 5; v++) {
            assertThat(Rating.of(v).value()).isEqualTo(v);
        }
    }

    @Test
    @DisplayName("rechaza valores fuera de 1..5")
    void outOfRange_throws() {
        assertThatThrownBy(() -> Rating.of(0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Rating.of(6)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Rating.of(-3)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("isPositive() es true para 4 y 5")
    void isPositive() {
        assertThat(Rating.of(5).isPositive()).isTrue();
        assertThat(Rating.of(4).isPositive()).isTrue();
        assertThat(Rating.of(3).isPositive()).isFalse();
        assertThat(Rating.of(1).isPositive()).isFalse();
    }

    @Test
    @DisplayName("toString devuelve el número")
    void toString_value() {
        assertThat(Rating.of(4).toString()).isEqualTo("4");
    }
}
