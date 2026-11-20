-- Simulations the server clock advances on its own, and how many ticks per step.
ALTER TABLE simulation ADD COLUMN running BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE simulation ADD COLUMN speed INT NOT NULL DEFAULT 5 CHECK (speed BETWEEN 1 AND 100);

CREATE INDEX simulation_running ON simulation (running) WHERE running;
