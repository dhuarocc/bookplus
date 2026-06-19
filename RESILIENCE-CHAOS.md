# Bulkhead + Chaos Engineering

Dos piezas que cierran la estrategia de resiliencia del catálogo: **aislamiento** de
recursos (bulkhead) y **verificación activa** de que la resiliencia funciona (Chaos Monkey).

## Bulkhead (mamparo)

El nombre viene de los compartimentos estancos de un barco: si uno se inunda, los demás
siguen a flote. En software, un *bulkhead* limita cuántas llamadas concurrentes puede tener
una operación, para que un recurso lento no agote todos los hilos y arrastre al resto del
servicio.

En catalog, la búsqueda en Elasticsearch lleva `@Bulkhead(name = "bookSearch")`:

```yaml
resilience4j:
  bulkhead:
    instances:
      bookSearch:
        max-concurrent-calls: 20   # como máximo 20 búsquedas a la vez
        max-wait-duration: 50ms    # si está lleno, espera 50ms y si no, rechaza
```

Si ES se pone lento y entran 200 peticiones, solo 20 van al cluster; las demás se rechazan
rápido (`BulkheadFullException`) y caen en el **mismo fallback** que ya teníamos (resultado
vacío). Así un Elasticsearch degradado no consume todos los hilos del catálogo: el resto de
la API (fichas de libro, categorías) sigue respondiendo.

Combina con lo ya existente en `search()`:

```
@CircuitBreaker(name="bookSearch", fallbackMethod="searchFallback")  // corta si falla mucho
@Bulkhead(name="bookSearch")                                          // aísla la concurrencia
@Retry(name="bookSearch")                                             // reintenta fallos puntuales
```

### Bulkhead vs TimeLimiter

El `TimeLimiter` de Resilience4j corta una llamada que tarda demasiado, pero **solo aplica a
métodos asíncronos** (`CompletableFuture`/reactivos), porque necesita interrumpir en otro
hilo. Como `search()` es síncrono, aquí el patrón correcto es el **bulkhead semáforo** (más
el `socket-timeout: 30s` del cliente ES, que ya acota la espera de red). El TimeLimiter se
añadiría si exponemos una variante reactiva de la búsqueda.

## Chaos Monkey (Chaos Engineering)

No basta con configurar resiliencia: hay que **comprobar** que de verdad protege. Chaos
Engineering inyecta fallos a propósito en un entorno controlado para ver cómo reacciona el
sistema. Usamos **Chaos Monkey for Spring Boot** (`de.codecentric`).

Está **desactivado por defecto** (`chaos.monkey.enabled: false` en `application.yml`) y solo
se enciende con el perfil `chaos` (`application-chaos.yml`), nunca en producción:

```
java -jar catalog.jar -Dspring.profiles.active=chaos
```

Bajo ese perfil, Chaos Monkey vigila los beans (`@Service`, `@Component`, repos, controllers)
e inyecta **latencia** (1–3 s) y **excepciones** en ~1 de cada 5 llamadas. Así se puede
observar en vivo que:

- el **circuit breaker** se abre cuando ES empieza a fallar,
- el **retry** reabsorbe fallos puntuales,
- el **bulkhead** rechaza el exceso de concurrencia sin tumbar el servicio,
- y el **fallback** mantiene la página de catálogo respondiendo.

Se puede activar/desactivar en caliente vía Actuator sin reiniciar:

```
POST /actuator/chaosmonkey/enable
POST /actuator/chaosmonkey/disable
GET  /actuator/chaosmonkey/status
```

## Por qué importa

Es la diferencia entre "creo que es resiliente" y "lo he verificado rompiéndolo a
propósito". Bancos y grandes empresas hacen exactamente esto (Netflix popularizó el Chaos
Monkey original) para ganar confianza en que el sistema aguanta fallos reales de producción.
