CREATE TABLE telemetry_event (
    id             BIGSERIAL    PRIMARY KEY,
    event_id       VARCHAR(120) NOT NULL UNIQUE,
    simulation_id  UUID         NOT NULL REFERENCES simulation (id),
    tick           BIGINT       NOT NULL,
    module         VARCHAR(20)  NOT NULL,
    payload        JSONB        NOT NULL,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX telemetry_event_lookup ON telemetry_event (simulation_id, module, tick);
