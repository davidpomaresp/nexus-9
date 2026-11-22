-- The featured simulation could be changed by anyone before it became read-only.
-- Drop it so the demo creates it again at its starting tick.
DELETE FROM telemetry_event WHERE simulation_id IN (SELECT id FROM simulation WHERE featured);
DELETE FROM incident WHERE simulation_id IN (SELECT id FROM simulation WHERE featured);
DELETE FROM simulation WHERE featured;
