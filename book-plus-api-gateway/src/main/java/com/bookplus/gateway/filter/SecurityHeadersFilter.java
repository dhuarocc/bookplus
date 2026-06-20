package com.bookplus.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global security headers filter.
 * Runs on every response before it is sent back to the client.
 *
 * Headers added:
 *  - X-Content-Type-Options: nosniff       (evita MIME-sniffing)
 *  - X-Frame-Options: DENY                 (evita clickjacking en iframes)
 *  - X-XSS-Protection: 1; mode=block       (protección XSS heredada; lo moderno es CSP)
 *  - Strict-Transport-Security             (fuerza HTTPS durante 1 año)
 *  - Referrer-Policy                       (limita la info de referrer)
 *  - Content-Security-Policy               (restringe orígenes de recursos)
 *  - Permissions-Policy                    (desactiva APIs sensibles del navegador)
 *  - Cross-Origin-Opener-Policy / -Resource-Policy  (aislamiento entre orígenes)
 *  - X-Permitted-Cross-Domain-Policies     (bloquea políticas cross-domain heredadas)
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            headers.set("X-Content-Type-Options",   "nosniff");
            headers.set("X-Frame-Options",           "DENY");
            headers.set("X-XSS-Protection",          "1; mode=block");
            headers.set("Referrer-Policy",           "strict-origin-when-cross-origin");

            // HSTS — only effective over HTTPS; safe to include here
            headers.set("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains");

            // Permissions Policy — disable sensitive browser APIs
            headers.set("Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=()");

            // Cross-origin isolation (OWASP) — aísla la app de otros orígenes/popups.
            headers.set("Cross-Origin-Opener-Policy",   "same-origin");
            headers.set("Cross-Origin-Resource-Policy", "same-origin");
            // Bloquea políticas cross-domain heredadas (Flash/PDF antiguos).
            headers.set("X-Permitted-Cross-Domain-Policies", "none");

            // Content Security Policy
            headers.set("Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'");
        }));
    }

    @Override
    public int getOrder() {
        // Run after routing but before response is committed
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
