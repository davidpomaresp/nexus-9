package com.nexus9.backend.telemetry;

import com.nexus9.backend.domain.StationModule;
import com.nexus9.simulator.StationSnapshot;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class TelemetryRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public TelemetryRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Stores one event per module. Events already stored are skipped, so appending the same
     * snapshot twice has no effect. Returns the number of new events.
     */
    public int append(UUID simulationId, StationSnapshot snapshot) {
        int inserted = 0;
        for (Map.Entry<StationModule, Map<String, Object>> e : toPayloads(snapshot).entrySet()) {
            inserted += jdbc.sql("""
                    INSERT INTO telemetry_event (event_id, simulation_id, tick, module, payload)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (event_id) DO NOTHING
                    """)
                    .params(eventId(simulationId, e.getKey(), snapshot.tick()), simulationId,
                            snapshot.tick(), e.getKey().name(), json.writeValueAsString(e.getValue()))
                    .update();
        }
        return inserted;
    }

    public List<TelemetryRecord> find(UUID simulationId, StationModule module, long fromTick, long toTick) {
        return jdbc.sql("""
                SELECT tick, module, payload::text AS payload FROM telemetry_event
                WHERE simulation_id = ? AND module = ? AND tick BETWEEN ? AND ?
                ORDER BY tick
                """)
                .params(simulationId, module.name(), fromTick, toTick)
                .query((rs, n) -> new TelemetryRecord(
                        rs.getLong("tick"),
                        StationModule.valueOf(rs.getString("module")),
                        json.readValue(rs.getString("payload"), MAP)))
                .list();
    }

    /** Every module between two ticks, inclusive, ordered by tick and module. */
    public List<TelemetryRecord> findAll(UUID simulationId, long fromTick, long toTick) {
        return jdbc.sql("""
                SELECT tick, module, payload::text AS payload FROM telemetry_event
                WHERE simulation_id = ? AND tick BETWEEN ? AND ?
                ORDER BY tick, module
                """)
                .params(simulationId, fromTick, toTick)
                .query((rs, n) -> new TelemetryRecord(
                        rs.getLong("tick"),
                        StationModule.valueOf(rs.getString("module")),
                        json.readValue(rs.getString("payload"), MAP)))
                .list();
    }

    /** All modules at one tick, so that a caller never mixes readings from different ticks. */
    public Map<StationModule, TelemetryRecord> atTick(UUID simulationId, long tick) {
        Map<StationModule, TelemetryRecord> records = new EnumMap<>(StationModule.class);
        jdbc.sql("""
                SELECT tick, module, payload::text AS payload FROM telemetry_event
                WHERE simulation_id = ? AND tick = ?
                """)
                .params(simulationId, tick)
                .query((rs, n) -> new TelemetryRecord(
                        rs.getLong("tick"),
                        StationModule.valueOf(rs.getString("module")),
                        json.readValue(rs.getString("payload"), MAP)))
                .list()
                .forEach(r -> records.put(r.module(), r));
        return records;
    }

    public long count(UUID simulationId) {
        return jdbc.sql("SELECT count(*) FROM telemetry_event WHERE simulation_id = ?")
                .param(simulationId)
                .query(Long.class)
                .single();
    }

    static String eventId(UUID simulationId, StationModule module, long tick) {
        return simulationId + ":" + module + ":" + tick;
    }

    static Map<StationModule, Map<String, Object>> toPayloads(StationSnapshot s) {
        Map<String, Object> power = new LinkedHashMap<>();
        power.put("sunlit", s.power().sunlit());
        power.put("generationKw", s.power().generationKw());
        power.put("demandKw", s.power().demandKw());
        power.put("servedKw", s.power().servedKw());
        power.put("unservedKw", s.power().unservedKw());
        power.put("batteryKwh", s.power().batteryKwh());
        power.put("batteryPercent", s.power().batteryPercent());

        Map<String, Object> oxygen = new LinkedHashMap<>();
        oxygen.put("generatorPowered", s.oxygen().generatorPowered());
        oxygen.put("levelPercent", s.oxygen().levelPercent());

        Map<String, Object> comms = new LinkedHashMap<>();
        comms.put("mode", s.comms().mode().name());

        Map<StationModule, Map<String, Object>> payloads = new LinkedHashMap<>();
        payloads.put(StationModule.POWER, power);
        payloads.put(StationModule.OXYGEN, oxygen);
        payloads.put(StationModule.COMMS, comms);
        return payloads;
    }
}
