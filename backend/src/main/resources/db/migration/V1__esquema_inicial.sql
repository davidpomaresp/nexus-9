-- Migración inicial. Las tablas de telemetría e incidencias llegan en las tareas 4 y 5.
-- Esta tabla registra cada simulación creada (semilla + escenario), base de todo lo demás.
CREATE TABLE simulation (
    id            UUID         PRIMARY KEY,
    seed          BIGINT       NOT NULL,
    scenario      VARCHAR(100) NOT NULL,
    current_tick  BIGINT       NOT NULL DEFAULT 0 CHECK (current_tick >= 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
