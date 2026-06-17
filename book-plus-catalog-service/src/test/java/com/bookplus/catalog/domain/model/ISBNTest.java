package com.bookplus.catalog.domain.model;

import com.bookplus.catalog.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ISBN (value object, ISBN-13)")
class ISBNTest {

    @Test
    @DisplayName("acepta un ISBN-13 válido y guarda solo dígitos")
    void valid_isbn13() {
        assertThat(ISBN.of("9780132350884").value()).isEqualTo("9780132350884");
    }

    @Test
    @DisplayName("normaliza guiones y espacios")
    void stripsSeparators() {
        assertThat(ISBN.of("978-0-13-235088-4").value()).isEqualTo("9780132350884");
        assertThat(ISBN.of("978 0 13 235088 4").value()).isEqualTo("9780132350884");
    }

    @Test
    @DisplayName("rechaza dígito de control inválido")
    void invalidCheckDigit_throws() {
        assertThatThrownBy(() -> ISBN.of("9780132350880"))
                .isInstanceOf(DomainException.class).hasMessageContaining("Invalid ISBN");
    }

    @Test
    @DisplayName("rechaza longitud incorrecta o caracteres no numéricos")
    void invalidFormat_throws() {
        assertThatThrownBy(() -> ISBN.of("12345")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ISBN.of("97801323508AX")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("null lanza NPE")
    void null_throws() {
        assertThatThrownBy(() -> ISBN.of(null)).isInstanceOf(NullPointerException.class);
    }
}
