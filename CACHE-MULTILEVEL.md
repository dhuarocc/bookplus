# Caché de dos niveles (Caffeine L1 + Redis L2)

El catálogo se lee muchísimo más de lo que se escribe, así que cachear los libros más
consultados ahorra trabajo a la base de datos y acelera las respuestas. Ya había una caché en
**Redis** (cache-aside); ahora añadimos un nivel más rápido **delante**: una *near-cache* en
memoria del proceso con **Caffeine**.

## Por qué dos niveles

- **L1 — Caffeine (en proceso)**: vive en la memoria de la instancia. Acceso en
  nanosegundos, **sin red**. Ideal para los pocos libros muy populares que se piden una y
  otra vez.
- **L2 — Redis (compartido)**: lo comparten todas las réplicas y sobrevive a reinicios.
  Más lento que L1 (hay un salto de red) pero consistente entre instancias.

Cada acierto en L1 evita un viaje a Redis; cada acierto en L2 evita un viaje a la base de
datos. Es el patrón de *near-cache* que usan sistemas de alto tráfico.

## Cómo funciona

`TwoLevelCacheAdapter` implementa el mismo puerto `CachePort` y envuelve al adaptador de
Redis:

- **Lectura**: mira L1 → si falla, mira L2 (Redis) y, si lo encuentra, **repuebla L1** →
  si también falla, el llamante irá a la base de datos.
- **Escritura / evicción**: se propagan a **ambos** niveles para no servir datos obsoletos.
  (Caffeine no evicta por patrón, así que ante un cambio amplio se invalida todo L1 —es
  local y se repuebla solo—; Redis sí evicta por patrón con precisión.)

L1 está acotada: máximo 10.000 entradas y expiración a los 5 minutos, para no crecer sin
control y refrescar con frecuencia. Se registran estadísticas (`recordStats`) para poder
medir el *hit ratio*.

## La integración

- **Dependencia**: `com.github.ben-manes.caffeine:caffeine` (versión gestionada por el BOM de
  Spring Boot).
- **`TwoLevelCacheAdapter`** (`@Primary`, implementa `CachePort`): los casos de uso siguen
  inyectando `CachePort` y reciben esta versión; el `RedisCacheAdapter` queda detrás como L2.
  No hay cambios en el resto del código.

## Verificación

`TwoLevelCacheAdapterTest` (unit, sin Spring ni Redis) comprueba: el segundo `get` se sirve
desde L1 sin volver a L2, `evict` invalida L1 y propaga a Redis, y `put` escribe en ambos
niveles. Corre en el `mvn test` normal.

## Consideraciones / siguiente nivel

- **Coherencia entre réplicas**: L1 es local, así que tras una escritura en otra instancia,
  una réplica podría servir su L1 hasta que expire (5 min). Para datos que deben ser
  inmediatamente consistentes entre réplicas, se puede invalidar L1 vía un canal pub/sub de
  Redis (mensaje de evicción), o reducir el TTL de L1.
- **Métricas**: exponer el `hit ratio` de Caffeine en Micrometer/Prometheus para ajustar
  tamaño y TTL.
