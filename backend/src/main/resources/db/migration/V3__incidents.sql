CREATE TABLE incident (
    id                UUID         PRIMARY KEY,
    simulation_id     UUID         NOT NULL REFERENCES simulation (id),
    module            VARCHAR(20)  NOT NULL,
    rule_code         VARCHAR(50)  NOT NULL,
    priority          VARCHAR(10)  NOT NULL,
    status            VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    opened_at_tick    BIGINT       NOT NULL,
    resolved_at_tick  BIGINT,
    affected_modules  TEXT[]       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- At most one open incident per rule and module in a simulation.
CREATE UNIQUE INDEX incident_one_open_per_rule
    ON incident (simulation_id, module, rule_code)
    WHERE status = 'OPEN';

CREATE INDEX incident_by_simulation ON incident (simulation_id, status);
