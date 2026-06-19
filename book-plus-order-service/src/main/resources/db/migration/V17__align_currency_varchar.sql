-- ============================================================
-- Alinear tipos CHAR(3) → VARCHAR(3) (consistencia con el mapeo JPA)
-- ============================================================
-- Las columnas de moneda se crearon en V1 como CHAR(3), pero las entidades JPA las
-- mapean como String (varchar). Hibernate 6 valida el esquema de forma estricta y
-- rechaza bpchar (CHAR) frente a varchar, lo que impediría arrancar con ddl-auto=validate.
-- V4 ya hizo esta misma corrección para shipping_country; aquí cerramos las dos que
-- quedaban. CHAR(3)→VARCHAR(3) es seguro: los valores ('USD'…) ocupan exactamente 3
-- caracteres, sin relleno que perder.

ALTER TABLE orders      ALTER COLUMN total_currency TYPE VARCHAR(3);
ALTER TABLE order_items ALTER COLUMN currency       TYPE VARCHAR(3);
