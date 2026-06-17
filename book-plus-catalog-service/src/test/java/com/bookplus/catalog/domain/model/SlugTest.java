package com.bookplus.catalog.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Slug (value object)")
class SlugTest {

    @Test
    @DisplayName("from() quita acentos, baja a minúsculas y une con guiones")
    void from_normalizesAccentsAndSpaces() {
        assertThat(Slug.from("El Señor de los Anillos").value()).isEqualTo("el-senor-de-los-anillos");
    }

    @Test
    @DisplayName("from() elimina caracteres no válidos y guiones de los extremos")
    void from_stripsSpecialCharsAndEdges() {
        assertThat(Slug.from("  ¡Hola, Mundo!  ").value()).isEqualTo("hola-mundo");
        assertThat(Slug.from("--Java & Spring--").value()).isEqualTo("java-spring");
    }

    @Test
    @DisplayName("of() acepta un slug directo")
    void of_direct() {
        assertThat(Slug.of("clean-code").value()).isEqualTo("clean-code");
    }

    @Test
    @DisplayName("slug en blanco lanza IllegalArgumentException")
    void blank_throws() {
        assertThatThrownBy(() -> Slug.of("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Slug.of(null)).isInstanceOf(NullPointerException.class);
    }
}
