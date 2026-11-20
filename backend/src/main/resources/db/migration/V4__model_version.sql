-- Simulations created before this column existed used model version 1.
ALTER TABLE simulation ADD COLUMN model_version INT NOT NULL DEFAULT 1;
ALTER TABLE simulation ALTER COLUMN model_version DROP DEFAULT;

-- One reading per module and tick, whatever the event id.
DROP INDEX telemetry_event_lookup;
CREATE UNIQUE INDEX telemetry_event_module_tick ON telemetry_event (simulation_id, module, tick);
