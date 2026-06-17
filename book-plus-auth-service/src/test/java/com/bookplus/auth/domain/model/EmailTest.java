package com.bookplus.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Email (value object)")
class EmailTest {

    @Test
    @DisplayName("normaliza a minúsculas y recorta espacios")
    void normalizes() {
        assertThat(Email.of("  Test@Example.COM  ").value()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("acepta formatos válidos")
    void validFormats() {
        assertThat(Email.of("a.b-c+d@sub.domain.io").value()).isEqualTo("a.b-c+d@sub.domain.io");
    }

    @Test
    @DisplayName("rechaza formatos inválidos")
    void invalidFormats_throw() {
        assertThatThrownBy(() -> Email.of("notanemail")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Email.of("a@b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Email.of("@example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Email.of("a@b.c")).isInstanceOf(IllegalArgumentException.class); // TLD < 2
    }

    @Test
    @DisplayName("null lanza NPE; toString devuelve el valor")
    void nullAndToString() {
        assertThatThrownBy(() -> Email.of(null)).isInstanceOf(NullPointerException.class);
        assertThat(Email.of("x@y.com").toString()).isEqualTo("x@y.com");
    }
}
