package com.bookplus.catalog.adapter.out.cache;

import com.bookplus.catalog.domain.model.Book;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Verifica la caché de dos niveles (Caffeine L1 + Redis L2) sin Spring ni Redis real.
 */
class TwoLevelCacheAdapterTest {

    private final RedisCacheAdapter l2 = mock(RedisCacheAdapter.class);
    private final TwoLevelCacheAdapter cache = new TwoLevelCacheAdapter(l2);

    @Test
    void el_segundo_get_se_sirve_desde_L1_sin_volver_a_L2() {
        Book book = mock(Book.class);
        when(l2.getBook("book:1")).thenReturn(Optional.of(book));

        Optional<Book> first  = cache.getBook("book:1");   // miss L1 -> va a L2 y repuebla
        Optional<Book> second = cache.getBook("book:1");   // acierto L1

        assertThat(first).containsSame(book);
        assertThat(second).containsSame(book);
        verify(l2, times(1)).getBook("book:1");            // L2 consultado UNA sola vez
    }

    @Test
    void evict_invalida_L1_y_propaga_a_L2() {
        Book book = mock(Book.class);
        when(l2.getBook("book:1")).thenReturn(Optional.of(book));
        cache.getBook("book:1");        // puebla L1

        cache.evictBook("book:1");      // debe invalidar L1 y Redis
        cache.getBook("book:1");        // L1 vacío -> vuelve a L2

        verify(l2).evictBook("book:1");
        verify(l2, times(2)).getBook("book:1");
    }

    @Test
    void put_escribe_en_ambos_niveles() {
        Book book = mock(Book.class);
        cache.putBook("book:9", book);

        verify(l2).putBook("book:9", book);
        assertThat(cache.getBook("book:9")).containsSame(book);
        verify(l2, never()).getBook("book:9");
    }
}
