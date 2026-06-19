# Idempotencia HTTP (Idempotency-Key, estilo Stripe)

En operaciones que mueven dinero o crean recursos, el cliente puede **reintentar** sin
querer: un timeout de red, un doble clic, un reintento automático del navegador… Sin
protección, cada reintento del checkout crea **otro pedido** (y otro posible cargo). La
idempotencia a nivel HTTP lo evita: la misma operación, repetida, produce **un solo**
efecto.

Es el patrón que usan APIs de pago serias (Stripe, PayPal): el cliente genera una
**`Idempotency-Key`** (un UUID) y la envía como cabecera; el servidor garantiza que esa
clave se procesa una sola vez.

## Cómo funciona

En `POST /api/v1/cart/checkout`, el flujo es:

1. El cliente manda la cabecera `Idempotency-Key: <uuid>`.
2. El servidor consulta la clave (namespaciada por usuario) y decide:
   - **PROCEED** — primera vez: marca la clave "en curso" y ejecuta el checkout.
   - **REPLAY** — ya se completó antes: devuelve el mismo resultado (`202`) **sin** volver
     a publicar el evento ni crear otro pedido.
   - **IN_PROGRESS** — otra petición con la misma clave se está procesando ahora mismo:
     responde **`409 Conflict`** (el cliente puede reintentar luego).
3. Si el checkout **falla**, la clave se **libera** para permitir un reintento legítimo.
4. Si la cabecera no se envía, el endpoint funciona como antes (compatibilidad hacia atrás).

## La integración

- **`IdempotencyService`** (lógica pura): decide PROCEED/REPLAY/IN_PROGRESS y gestiona el
  ciclo `begin → complete | cancel`.
- **`IdempotencyStore`** (puerto) + **`RedisIdempotencyStore`** (adaptador): las claves se
  guardan en **Redis** (ya presente en cart-service) con **TTL**, así caducan solas
  (1 min "en curso", 24 h "completado"). Redis es ideal: rápido, compartido entre réplicas
  y con expiración nativa.
- **`CartController`** lee la cabecera `Idempotency-Key` y aplica el servicio alrededor del
  caso de uso de checkout.

## Por qué Redis y no una tabla

El checkout de cart-service no usa base de datos relacional (trabaja con Redis y publica un
evento Kafka). Redis encaja de forma natural: las claves de idempotencia son efímeras y el
TTL las limpia sin trabajo extra. Además, al ser un store compartido, el límite es **global**
aunque haya varias réplicas del servicio.

## Verificación

`IdempotencyServiceTest` (unit, sin Redis, con un store en memoria) cubre: primera vez
procede, reintento en curso da IN_PROGRESS, tras completar da REPLAY, cancelar libera la
clave, y que distintas claves/usuarios son independientes. Corre en el `mvn test` normal.

## Relación con la idempotencia que ya existía

Ya había un **Idempotent Consumer** en los consumidores Kafka (para no procesar dos veces el
mismo evento). Esto es complementario: cubre la **capa HTTP de entrada** (el cliente), de
modo que el evento duplicado ni siquiera llega a publicarse. Defensa en profundidad.

## Siguiente nivel

- **Cachear el cuerpo de la respuesta** para endpoints que devuelven datos (aquí el checkout
  responde `202` sin cuerpo, así que basta con replicar el estado).
- **Huella del request** (hash del body): si llega la misma clave con un body distinto,
  responder `422` para detectar usos incorrectos de la clave, como hace Stripe.
