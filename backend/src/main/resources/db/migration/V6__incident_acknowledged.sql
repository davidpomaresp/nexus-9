-- Last completed tick when an operator acknowledged the incident; null until then.
ALTER TABLE incident ADD COLUMN acknowledged_at_tick BIGINT;
