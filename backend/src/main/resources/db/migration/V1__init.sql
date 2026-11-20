CREATE TABLE simulation (
    id            UUID         PRIMARY KEY,
    seed          BIGINT       NOT NULL,
    scenario      VARCHAR(100) NOT NULL,
    current_tick  BIGINT       NOT NULL DEFAULT 0 CHECK (current_tick >= 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
