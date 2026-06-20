package com.bookplus.catalog.adapter.out.cache;

import com.bookplus.catalog.domain.model.Book;
import com.bookplus.catalog.domain.port.out.CachePort;
import com.bookplus.catalog.shared.annotation.PersistenceAdapter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Optional;

/**
 * Caché de dos niveles (near-cache) para el catálogo de libros.
 *
 * L1 = Caffeine, en memoria del proceso: rapidísimo (nanosegundos), sin red. Es la primera
 * parada de cada lectura.
 * L2 = Redis ({@link RedisCacheAdapter}): compartido entre réplicas, sobrevive a reinicios.
 *
 * Flujo de lectura: L1 → (fallo) L2 → (fallo) quien llame irá a la BD. Cada acierto en L2
 * repuebla L1. Las escrituras y evicciones se propagan a ambos niveles para no servir datos
 * obsoletos. Reduce drásticamente los viajes a Redis para los libros más consultados.
 *
 * Es @Primary: los casos de uso siguen inyectando {@link CachePort} y reciben esta versión;
 * el adaptador Redis queda como L2 detrás.
 */
@PersistenceAdapter
@Primary
@Slf4j
public class TwoLevelCacheAdapter implements CachePort {

    private final RedisCacheAdapter l2;
    private final Cache<String, Book> l1;

    public TwoLevelCacheAdapter(RedisCacheAdapter l2) {
        this.l2 = l2;
        this.l1 = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
    }

    @Override
    public Optional<Book> getBook(String key) {
        Book cached = l1.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);              // acierto L1
        }
        Optional<Book> fromL2 = l2.getBook(key);     // mira en Redis
        fromL2.ifPresent(book -> l1.put(key, book)); // repuebla L1
        return fromL2;
    }

    @Override
    public void putBook(String key, Book book) {
        l1.put(key, book);
        l2.putBook(key, book);
    }

    @Override
    public void evictBook(String key) {
        l1.invalidate(key);
        l2.evictBook(key);
    }

    @Override
    public void evictByPattern(String pattern) {
        // Caffeine no soporta evicción por patrón; ante un cambio amplio invalidamos todo L1
        // (es local y se repuebla solo). Redis sí evicta por patrón con precisión.
        l1.invalidateAll();
        l2.evictByPattern(pattern);
    }
}
