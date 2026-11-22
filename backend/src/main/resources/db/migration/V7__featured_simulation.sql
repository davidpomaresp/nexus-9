-- The simulation the public demo opens with. At most one at a time.
ALTER TABLE simulation ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;

CREATE UNIQUE INDEX simulation_one_featured ON simulation (featured) WHERE featured;
