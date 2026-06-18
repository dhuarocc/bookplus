# Bloqueo optimista (Optimistic Locking)

Bajo concurrencia, dos procesos pueden leer la misma fila, modificarla y guardarla casi a la
vez. Sin control, **la segunda escritura pisa a la primera** sin que nadie se entere (*lost
update*) — en una tienda, esto puede significar **vender stock que ya no existe** o cobrar dos
veces. El bloqueo optimista lo evita.

## Cómo funciona

Cada entidad lleva una columna `version`. Hibernate la incluye en el `UPDATE`:

```sql
UPDATE stocks SET quantity_available = ?, version = version + 1
WHERE id = ? AND version = ?      -- la versión que se leyó
```

Si entre la lectura y la escritura otro proceso ya modificó la fila (y subió la versión), el
`WHERE version = ?` no encaja, el UPDATE afecta 0 filas y Hibernate lanza
`OptimisticLockException`. La operación se rechaza de forma **controlada** en vez de corromper
los datos; el llamador puede reintentar con el dato fresco.

Se llama "optimista" porque no bloquea la fila por adelantado (a diferencia del pesimista):
asume que los conflictos son raros y solo actúa si de verdad ocurre uno. Es ideal para alta
concurrencia de lectura.

## Dónde está aplicado

En los agregados donde la concurrencia importa de verdad:

- **Stock** (inventory) — evita el *overselling* cuando dos reservas concurrentes descuentan el
  mismo stock disponible.
- **Payment** (payment) — evita actualizaciones pisadas del mismo pago.
- **Order** (order) — evita que dos cambios de estado simultáneos se pisen.

Cada uno: `@Version` en la entidad JPA, una migración Flyway que añade la columna `version`, y
la versión hilada de ida y vuelta por el modelo de dominio y el mapper (para que el control
funcione con el patrón hexagonal de mapeo, no solo en la entidad).

## Optimista vs pesimista

- **Optimista** (lo implementado): sin bloqueos, detecta el conflicto al guardar. Mejor para
  mucha lectura y poca colisión.
- **Pesimista** (`SELECT ... FOR UPDATE`): bloquea la fila al leer. Útil cuando las colisiones
  son frecuentes y caras de reintentar. Se podría añadir en el camino de reserva de stock como
  refuerzo adicional.

## Siguiente nivel

- **Reintento automático** ante `OptimisticLockException` (p. ej. con Spring Retry) en las
  operaciones idempotentes, para que el conflicto se resuelva solo sin molestar al usuario.
