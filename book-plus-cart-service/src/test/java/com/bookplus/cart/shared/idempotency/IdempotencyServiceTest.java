package com.bookplus.cart.shared.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la lógica de idempotencia sin Redis, con un store en memoria.
 */
class IdempotencyServiceTest {

    /** Store en memoria (ignora el TTL; suficiente para validar la lógica). */
    static class InMemoryStore implements IdempotencyStore {
        final Map<String, String> map = new HashMap<>();
        public boolean putIfAbsent(String k, String v, Duration ttl) { return map.putIfAbsent(k, v) == null; }
        public Optional<String> get(String k) { return Optional.ofNullable(map.get(k)); }
        public void set(String k, String v, Duration ttl) { map.put(k, v); }
        public void delete(String k) { map.remove(k); }
    }

    private final IdempotencyService service =
            new IdempotencyService(new InMemoryStore(), Duration.ofMinutes(1), Duration.ofHours(24));

    @Test
    void primera_vez_procede_y_tras_completar_reproduce() {
        assertThat(service.begin("user-1", "key-A")).isEqualTo(IdempotencyService.Decision.PROCEED);
        // mientras está en curso, un reintento concurrente recibe IN_PROGRESS
        assertThat(service.begin("user-1", "key-A")).isEqualTo(IdempotencyService.Decision.IN_PROGRESS);
        // al completar, los reintentos reciben REPLAY (no se re-ejecuta)
        service.complete("user-1", "key-A");
        assertThat(service.begin("user-1", "key-A")).isEqualTo(IdempotencyService.Decision.REPLAY);
    }

    @Test
    void cancelar_libera_la_clave_para_reintentar() {
        assertThat(service.begin("user-1", "key-B")).isEqualTo(IdempotencyService.Decision.PROCEED);
        service.cancel("user-1", "key-B");   // la operación falló
        assertThat(service.begin("user-1", "key-B")).isEqualTo(IdempotencyService.Decision.PROCEED);
    }

    @Test
    void claves_y_usuarios_distintos_son_independientes() {
        service.complete("user-1", "key-A");
        assertThat(service.begin("user-1", "key-A")).isEqualTo(IdempotencyService.Decision.REPLAY);
        assertThat(service.begin("user-2", "key-A")).isEqualTo(IdempotencyService.Decision.PROCEED); // otro usuario
        assertThat(service.begin("user-1", "key-Z")).isEqualTo(IdempotencyService.Decision.PROCEED); // otra clave
    }
}
