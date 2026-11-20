package com.nexus9.backend.incident;

import com.nexus9.backend.domain.StationModule;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {

    private static final String COLUMNS = """
            id, simulation_id, module, rule_code, priority, status,
            opened_at_tick, resolved_at_tick, affected_modules
            """;

    private final JdbcClient jdbc;

    public IncidentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns false if an open incident for the same rule already exists. */
    public boolean insertOpen(Incident incident) {
        String[] affected = incident.affectedModules().stream().map(Enum::name).toArray(String[]::new);
        int rows = jdbc.sql("""
                INSERT INTO incident (id, simulation_id, module, rule_code, priority, status,
                                      opened_at_tick, affected_modules)
                VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?)
                ON CONFLICT (simulation_id, module, rule_code) WHERE status = 'OPEN' DO NOTHING
                """)
                .params(incident.id(), incident.simulationId(), incident.module().name(), incident.ruleCode(),
                        incident.priority().name(), incident.openedAtTick(), affected)
                .update();
        return rows == 1;
    }

    public Optional<Incident> findOpen(UUID simulationId, StationModule module, String ruleCode) {
        return jdbc.sql("SELECT " + COLUMNS + """
                FROM incident
                WHERE simulation_id = ? AND module = ? AND rule_code = ? AND status = 'OPEN'
                """)
                .params(simulationId, module.name(), ruleCode)
                .query(this::map)
                .optional();
    }

    public void resolve(UUID id, long tick) {
        jdbc.sql("UPDATE incident SET status = 'RESOLVED', resolved_at_tick = ? WHERE id = ? AND status = 'OPEN'")
                .params(tick, id)
                .update();
    }

    public int countOpen(UUID simulationId) {
        return jdbc.sql("SELECT count(*) FROM incident WHERE simulation_id = ? AND status = 'OPEN'")
                .param(simulationId)
                .query(Integer.class)
                .single();
    }

    public List<Incident> findBySimulation(UUID simulationId, Optional<IncidentStatus> status) {
        return jdbc.sql("SELECT " + COLUMNS + """
                FROM incident
                WHERE simulation_id = :simulationId AND (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                ORDER BY opened_at_tick, rule_code
                """)
                .param("simulationId", simulationId)
                .param("status", status.map(Enum::name).orElse(null))
                .query(this::map)
                .list();
    }

    private Incident map(ResultSet rs, int row) throws SQLException {
        return new Incident(
                rs.getObject("id", UUID.class),
                rs.getObject("simulation_id", UUID.class),
                StationModule.valueOf(rs.getString("module")),
                rs.getString("rule_code"),
                Priority.valueOf(rs.getString("priority")),
                IncidentStatus.valueOf(rs.getString("status")),
                rs.getLong("opened_at_tick"),
                rs.getObject("resolved_at_tick", Long.class),
                modules(rs.getArray("affected_modules")));
    }

    private static List<StationModule> modules(Array array) throws SQLException {
        return Arrays.stream((String[]) array.getArray()).map(StationModule::valueOf).toList();
    }
}
