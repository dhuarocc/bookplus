package com.bookplus.cart.shared.idempotency;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Idempotencia estilo Stripe para operaciones mutadoras (p. ej. checkout).
 *
 * El cliente envía una cabecera Idempotency-Key. Si reintenta la misma operación (timeout,
 * doble clic, reintento de red) con la misma clave, el servidor NO la vuelve a ejecutar:
 *  - PROCEED      → primera vez, hay que procesarla (la clave queda marcada "en curso").
 *  - REPLAY       → ya se completó antes; devolver el mismo resultado sin re-ejecutar.
 *  - IN_PROGRESS  → otra petición con la misma clave se está procesando ahora (responder 409).
 *
 * Las claves caducan solas vía TTL de Redis. Si la operación falla, se cancela la marca para
 * permitir un reintento legítimo.
 */
@Component
public class IdempotencyService {

    public enum Decision { PROCEED, REPLAY, IN_PROGRESS }

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String DONE = "DONE";

    private final IdempotencyStore store;
    private final Duration inProgressTtl;
    private final Duration doneTtl;

    public IdempotencyService(IdempotencyStore store) {
        this(store, Duration.ofMinutes(1), Duration.ofHours(24));
    }

    IdempotencyService(IdempotencyStore store, Duration inProgressTtl, Duration doneTtl) {
        this.store = store;
        this.inProgressTtl = inProgressTtl;
        this.doneTtl = doneTtl;
    }

    public Decision begin(String namespace, String idempotencyKey) {
        String k = key(namespace, idempotencyKey);
        var existing = store.get(k);
        if (existing.isPresent()) {
            return DONE.equals(existing.get()) ? Decision.REPLAY : Decision.IN_PROGRESS;
        }
        boolean started = store.putIfAbsent(k, IN_PROGRESS, inProgressTtl);
        return started ? Decision.PROCEED : Decision.IN_PROGRESS; // perdió la carrera
    }

    /** Marca la operación como completada (se devolverá REPLAY en reintentos durante el TTL). */
    public void complete(String namespace, String idempotencyKey) {
        store.set(key(namespace, idempotencyKey), DONE, doneTtl);
    }

    /** Libera la clave si la operación falló, para permitir reintentar. */
    public void cancel(String namespace, String idempotencyKey) {
        store.delete(key(namespace, idempotencyKey));
    }

    private String key(String namespace, String idempotencyKey) {
        return "idem:" + namespace + ":" + idempotencyKey;
    }
}
