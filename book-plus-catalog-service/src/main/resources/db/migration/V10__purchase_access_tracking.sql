-- ============================================================
-- Catalog Service — V10: tracking de consumo y revocación de acceso
-- ============================================================
-- Soporta la política de reembolsos de productos digitales:
--   * active        → false revoca el acceso al PDF (p. ej. tras un reembolso).
--   * downloaded    → si el usuario llegó a descargar/abrir el libro.
--   * read_progress → porcentaje de lectura (0-100), umbral de "consumido".

ALTER TABLE user_purchases
    ADD COLUMN IF NOT EXISTS active        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS downloaded    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS read_progress INTEGER NOT NULL DEFAULT 0;
