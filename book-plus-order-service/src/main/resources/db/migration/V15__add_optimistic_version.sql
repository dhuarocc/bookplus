-- Bloqueo optimista: columna de versión para la tabla orders.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
