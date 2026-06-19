package com.bookplus.cart.shared.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Almacén de claves de idempotencia (puerto). La implementación de producción usa Redis,
 * con expiración automática (TTL). Se abstrae para poder testear la lógica sin infraestructura.
 */
public interface IdempotencyStore {

    /** Inserta solo si la clave no existe (SET NX) con TTL. true = se insertó (éramos los primeros). */
    boolean putIfAbsent(String key, String value, Duration ttl);

    Optional<String> get(String key);

    /** Inserta/reemplaza con TTL. */
    void set(String key, String value, Duration ttl);

    void delete(String key);
}
