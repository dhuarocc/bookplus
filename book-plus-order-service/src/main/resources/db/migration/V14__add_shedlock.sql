-- ShedLock: bloqueo distribuido para tareas @Scheduled.
-- Evita que el outbox relay (y otros jobs) se ejecuten en paralelo cuando hay
-- varias réplicas de order-service.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
