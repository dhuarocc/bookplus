package com.bookplus.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el filtro añade todas las cabeceras de seguridad esperadas en cada respuesta.
 * Sin Spring: usa un ServerWebExchange simulado y una cadena que completa de inmediato.
 */
class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void anade_todas_las_cabeceras_de_seguridad() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/books").build());
        GatewayFilterChain chain = ex -> Mono.empty();   // no hay downstream

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders h = exchange.getResponse().getHeaders();
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(h.getFirst("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(h.getFirst("Permissions-Policy")).contains("geolocation=()");
        assertThat(h.getFirst("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(h.getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
        // Cabeceras de aislamiento modernas añadidas
        assertThat(h.getFirst("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(h.getFirst("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat(h.getFirst("X-Permitted-Cross-Domain-Policies")).isEqualTo("none");
    }
}
