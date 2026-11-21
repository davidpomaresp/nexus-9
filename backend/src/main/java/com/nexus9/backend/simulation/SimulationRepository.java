package com.nexus9.backend.simulation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SimulationRepository {

    private static final String SELECT = """
            SELECT id, seed, scenario, current_tick, model_version, running, speed FROM simulation
            """;

    private final JdbcClient jdbc;

    public SimulationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Simulation simulation) {
        jdbc.sql("""
                INSERT INTO simulation (id, seed, scenario, current_tick, model_version, running, speed)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(simulation.id(), simulation.seed(), simulation.scenario(), simulation.currentTick(),
                        simulation.modelVersion(), simulation.running(), simulation.speed())
                .update();
    }

    public Optional<Simulation> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = ?")
                .param(id)
                .query(Simulation.class)
                .optional();
    }

    /** Locks the row until the end of the transaction, so ticks of one simulation never run in parallel. */
    public Optional<Simulation> findByIdForUpdate(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = ? FOR UPDATE")
                .param(id)
                .query(Simulation.class)
                .optional();
    }

    public List<Simulation> findRunning() {
        return jdbc.sql(SELECT + "WHERE running ORDER BY created_at")
                .query(Simulation.class)
                .list();
    }

    public void updateClock(UUID id, boolean running, int speed) {
        jdbc.sql("UPDATE simulation SET running = ?, speed = ? WHERE id = ?")
                .params(running, speed, id)
                .update();
    }

    /** Deletes simulations created before {@code cutoff} with their telemetry and incidents. */
    public List<UUID> deleteCreatedBefore(Instant cutoff) {
        List<UUID> ids = jdbc.sql("SELECT id FROM simulation WHERE created_at < ?")
                .param(Timestamp.from(cutoff))
                .query(UUID.class)
                .list();
        for (UUID id : ids) {
            jdbc.sql("DELETE FROM telemetry_event WHERE simulation_id = ?").param(id).update();
            jdbc.sql("DELETE FROM incident WHERE simulation_id = ?").param(id).update();
            jdbc.sql("DELETE FROM simulation WHERE id = ?").param(id).update();
        }
        return ids;
    }

    public void updateCurrentTick(UUID id, long currentTick) {
        jdbc.sql("UPDATE simulation SET current_tick = ? WHERE id = ?")
                .params(currentTick, id)
                .update();
    }
}
