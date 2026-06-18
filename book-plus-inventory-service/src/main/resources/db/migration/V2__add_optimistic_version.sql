-- Bloqueo optimista: columna de versión para la tabla stocks (evita overselling
-- cuando dos reservas concurrentes intentan descontar el mismo stock disponible).
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
