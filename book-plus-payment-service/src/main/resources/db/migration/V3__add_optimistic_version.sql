-- Bloqueo optimista: columna de versión para la tabla payments.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
