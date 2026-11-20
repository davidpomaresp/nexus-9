package com.nexus9.backend.telemetry;

import com.nexus9.backend.domain.StationModule;
import java.util.Map;

public record TelemetryRecord(long tick, StationModule module, Map<String, Object> values) {
}
