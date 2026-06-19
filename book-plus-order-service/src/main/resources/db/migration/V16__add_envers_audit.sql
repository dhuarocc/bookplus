-- ============================================================
-- Hibernate Envers — audit trail del pedido
-- ============================================================
-- Cada cambio en una entidad @Audited (OrderEntity) genera:
--   · una fila en revinfo  (la "revisión": número + timestamp)
--   · una fila en orders_aud (el estado del pedido en esa revisión + tipo de operación)
-- Con ddl-auto=validate, estas tablas las crea Flyway (no Hibernate). Los nombres de
-- tabla/secuencia casan con CustomRevisionEntity y con la convención <tabla>_aud de Envers.

-- ── Secuencia y tabla de revisiones ─────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE revinfo (
    rev      INTEGER NOT NULL,
    revtstmp BIGINT,
    PRIMARY KEY (rev)
);

-- ── Tabla de auditoría de orders ────────────────────────────────────────────
-- Refleja las columnas auditadas de OrderEntity. Todas nulables salvo la PK
-- (id, rev), porque una revisión de borrado solo guarda la clave. revtype:
-- 0 = ADD (insert), 1 = MOD (update), 2 = DEL (delete).
CREATE TABLE orders_aud (
    id                      UUID          NOT NULL,
    rev                     INTEGER       NOT NULL,
    revtype                 SMALLINT,

    version                 BIGINT,
    user_id                 VARCHAR(255),
    user_email              VARCHAR(160),
    cart_id                 VARCHAR(255),
    status                  VARCHAR(30),
    total_amount            NUMERIC(12,2),
    total_currency          VARCHAR(3),

    shipping_recipient_name VARCHAR(255),
    shipping_street         VARCHAR(500),
    shipping_city           VARCHAR(255),
    shipping_state          VARCHAR(255),
    shipping_postal_code    VARCHAR(20),
    shipping_country        VARCHAR(60),

    payment_method          VARCHAR(20),
    delivery_type           VARCHAR(20),
    payment_id              VARCHAR(255),
    carrier                 VARCHAR(80),
    tracking_number         VARCHAR(120),
    delivery_code           VARCHAR(12),
    received_by             VARCHAR(120),
    assigned_courier        VARCHAR(255),
    assigned_courier_name   VARCHAR(120),
    claim_status            VARCHAR(20),
    claim_reason            VARCHAR(500),
    claim_resolution        VARCHAR(500),
    coupon_code             VARCHAR(40),
    discount_amount         NUMERIC(12,2),
    created_at              TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ,

    PRIMARY KEY (id, rev),
    CONSTRAINT fk_orders_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE INDEX idx_orders_aud_rev ON orders_aud (rev);
