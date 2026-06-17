package com.bookplus.catalog.domain.model;

import com.bookplus.catalog.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Category (aggregate)")
class CategoryTest {

    @Test
    @DisplayName("create() genera slug desde el nombre y queda activa")
    void create_generatesSlugAndIsActive() {
        Category c = Category.create("Ciencia Ficción", "Libros de sci-fi", null, null);

        assertThat(c.getId()).isNotNull();
        assertThat(c.getName()).isEqualTo("Ciencia Ficción");
        assertThat(c.getSlug().value()).isEqualTo("ciencia-ficcion");
        assertThat(c.isActive()).isTrue();
        assertThat(c.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("update() cambia nombre, slug, descripción e imagen")
    void update_changesFields() {
        Category c = Category.create("Terror", null, null, null);
        c.update("Suspenso", "nueva desc", "img.jpg");

        assertThat(c.getName()).isEqualTo("Suspenso");
        assertThat(c.getSlug().value()).isEqualTo("suspenso");
        assertThat(c.getDescription()).isEqualTo("nueva desc");
        assertThat(c.getImageUrl()).isEqualTo("img.jpg");
    }

    @Test
    @DisplayName("deactivate() desactiva; hacerlo dos veces lanza DomainException")
    void deactivate_thenAgain_throws() {
        Category c = Category.create("Historia", null, null, null);
        c.deactivate();
        assertThat(c.isActive()).isFalse();
        assertThatThrownBy(c::deactivate).isInstanceOf(DomainException.class).hasMessageContaining("already inactive");
    }

    @Test
    @DisplayName("nombre en blanco o demasiado largo lanza DomainException")
    void invalidName_throws() {
        assertThatThrownBy(() -> Category.create("   ", null, null, null))
                .isInstanceOf(DomainException.class).hasMessageContaining("blank");
        String tooLong = "x".repeat(101);
        assertThatThrownBy(() -> Category.create(tooLong, null, null, null))
                .isInstanceOf(DomainException.class).hasMessageContaining("exceed");
    }

    @Test
    @DisplayName("equals/hashCode por identidad (id)")
    void equalsByIdentity() {
        Category a = Category.create("A", null, null, null);
        Category b = Category.create("A", null, null, null);
        assertThat(a).isEqualTo(a).isNotEqualTo(b);   // distinto id
        assertThat(a.hashCode()).isEqualTo(a.hashCode());
    }
}
