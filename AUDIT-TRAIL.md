# Audit trail con Hibernate Envers

En banca y empresas reguladas no basta con saber el **estado actual** de un pedido: hay que
poder responder *quién lo cambió, cuándo y cómo era antes*. Eso es un **audit trail**
(registro de auditoría), y suele ser un requisito de cumplimiento. Lo implementamos en
order-service con **Hibernate Envers**.

## Cómo funciona

Envers se engancha en el ciclo de persistencia de Hibernate. Cada vez que se inserta,
modifica o borra una entidad anotada con `@Audited`, escribe automáticamente:

- una fila en **`revinfo`** — la *revisión*: un número incremental y un timestamp (el "punto
  en el tiempo" de un cambio; todas las modificaciones de una misma transacción comparten
  revisión),
- una fila en **`orders_aud`** — una copia del estado del pedido en esa revisión, más una
  columna `revtype` que indica la operación (`0` = alta, `1` = modificación, `2` = borrado).

Así queda un historial completo: se puede reconstruir el pedido tal y como estaba en
cualquier revisión, comparar versiones y ver la secuencia de cambios de estado
(`CONFIRMED → SHIPPED → DELIVERED`, etc.).

## Qué se audita

`OrderEntity` lleva `@Audited` a nivel de clase (la cabecera del pedido: estado, importes,
dirección, pago, tracking, reclamos…). La colección de líneas (`items`) va con `@NotAudited`
porque son inmutables tras la compra; auditar la cabecera es suficiente y mantiene el
esquema simple.

## Detalles de la integración

- **Dependencia**: `org.hibernate.orm:hibernate-envers` (se activa por estar en el
  classpath; no requiere configuración extra).
- **Entidad de revisión propia** (`CustomRevisionEntity`, `@RevisionEntity`): en vez de la
  `DefaultRevisionEntity`, definimos la nuestra para controlar de forma **determinista** los
  nombres de tabla (`revinfo`) y secuencia (`revinfo_seq`, `allocationSize = 1`). Esto es
  clave porque el servicio arranca con `ddl-auto: validate`: el esquema lo crea Flyway, no
  Hibernate, así que ambos deben coincidir exactamente. Tener entidad propia deja además la
  puerta abierta a guardar *quién* hizo el cambio (el usuario autenticado) en el futuro.
- **Migración** `V16__add_envers_audit.sql`: crea `revinfo_seq`, `revinfo` y `orders_aud`
  (esta última espeja las columnas auditadas, todas nulables salvo la PK `(id, rev)`, porque
  una revisión de borrado solo guarda la clave).

## Cómo consultarlo

Programáticamente, con el `AuditReader`:

```java
AuditReader reader = AuditReaderFactory.get(entityManager);

// Todas las revisiones de un pedido
List<Number> revs = reader.getRevisions(OrderEntity.class, orderId);

// El pedido tal y como estaba en una revisión concreta
OrderEntity snapshot = reader.find(OrderEntity.class, orderId, revs.get(0));
```

## Verificación

El test `OrderAuditTrailIT` (Testcontainers + Postgres real) persiste un pedido, cambia su
estado en otra transacción y comprueba que quedan **2 revisiones** con el estado correcto en
cada una. Va etiquetado `@Tag("integration")`, así que **no** corre en el `mvn test` normal
(que sigue siendo de pura unidad y rápido). Para ejecutarlo (requiere Docker):

```bash
mvn test -Dgroups=integration
```

## Siguiente nivel

- **Usuario que realiza el cambio**: añadir un campo a `CustomRevisionEntity` y rellenarlo
  con el usuario autenticado (`SecurityContext`) vía un `RevisionListener`, para un audit
  trail completo "quién / cuándo / qué".
- **Extender a otros agregados**: `Payment` y `Stock` son los siguientes candidatos
  naturales si se necesita trazabilidad regulatoria de cobros y existencias.
