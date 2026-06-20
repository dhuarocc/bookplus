# Cabeceras de seguridad HTTP (API Gateway)

El navegador toma muchas decisiones de seguridad a partir de las **cabeceras** que devuelve
el servidor: si puede meterse en un iframe, qué orígenes pueden cargar scripts, si fuerza
HTTPS, etc. Centralizamos esas cabeceras en el **API Gateway**, de modo que **todas** las
respuestas de todos los microservicios salen ya endurecidas, en un solo sitio.

## Cómo funciona

Un `SecurityHeadersFilter` (un `GlobalFilter` de Spring Cloud Gateway) se ejecuta en **cada
respuesta**, justo antes de enviarla al cliente, y añade el conjunto de cabeceras de
seguridad recomendado por OWASP.

## Cabeceras añadidas

- **`Content-Security-Policy`** — la más importante: restringe de qué orígenes se pueden
  cargar scripts, estilos, imágenes y conexiones (`default-src 'self'`…), y con
  `frame-ancestors 'none'` impide que la app se incruste en otros sitios. Es la defensa
  principal contra XSS e inyección de contenido.
- **`Strict-Transport-Security`** (HSTS) — fuerza HTTPS durante un año (`max-age=31536000;
  includeSubDomains`), evitando *downgrade* a HTTP.
- **`X-Content-Type-Options: nosniff`** — impide que el navegador "adivine" el tipo MIME
  (evita ejecutar como script algo que no lo es).
- **`X-Frame-Options: DENY`** — anti-clickjacking (refuerza `frame-ancestors`).
- **`Referrer-Policy: strict-origin-when-cross-origin`** — limita la información de referrer
  que se filtra a terceros.
- **`Permissions-Policy`** — desactiva APIs sensibles del navegador (cámara, micrófono,
  geolocalización, pago) que la app no necesita.
- **`Cross-Origin-Opener-Policy: same-origin`** y **`Cross-Origin-Resource-Policy:
  same-origin`** *(añadidas)* — aislamiento entre orígenes: protegen frente a ataques de
  canal lateral (Spectre) y a que otros sitios abran/abusen de la ventana.
- **`X-Permitted-Cross-Domain-Policies: none`** *(añadida)* — bloquea políticas cross-domain
  heredadas (Flash/PDF antiguos).
- **`X-XSS-Protection: 1; mode=block`** — protección XSS heredada (los navegadores modernos
  se apoyan en CSP).

## Por qué en el Gateway

Poner las cabeceras en el punto de entrada único significa que se aplican de forma
**consistente** a todo el tráfico saliente, sin tener que repetir la configuración en cada
microservicio ni arriesgarse a que uno se quede sin endurecer.

## Verificación

`SecurityHeadersFilterTest` (unit, con un `ServerWebExchange` simulado, sin Spring) comprueba
que **todas** las cabeceras esperadas están presentes en la respuesta. Actúa como guardia de
regresión: si alguien quita o cambia una, el test falla. Corre en el `mvn test` normal.

## Afinado para producción

- **CSP más estricta**: el `script-src` incluye `'unsafe-inline'` (lo necesita Swagger UI).
  En producción conviene servir la documentación aparte y endurecer el CSP quitando
  `'unsafe-inline'` y usando *nonces*/hashes.
- **HSTS preload**: añadir `preload` y registrar el dominio en la lista de preload de los
  navegadores cuando todo el tráfico sea HTTPS de forma definitiva.
