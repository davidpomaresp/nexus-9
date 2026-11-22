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
            SELECT id, seed, scenario, current_tick, model_version, running, speed, featured FROM simulation
            """;

    private static final long CREATE_LOCK = 0x4E455855L;

    private final JdbcClient jdbc;

    public SimulationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Simulation simulation) {
        jdbc.sql("""
                INSERT INTO simulation (id, seed, scenario, current_tick, model_version, running, speed, featured)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(simulation.id(), simulation.seed(), simulation.scenario(), simulation.currentTick(),
                        simulation.modelVersion(), simulation.running(), simulation.speed(), simulation.featured())
                .update();
    }

    /**
     * Inserts unless there are already {@code max} simulations (0 means no limit). The advisory lock
     * serialises concurrent creations, so the count cannot be read twice before either insert.
     */
    public boolean insertIfFewerThan(Simulation simulation, int max) {
        if (max > 0) {
            jdbc.sql("SELECT pg_advisory_xact_lock(?)").param(CREATE_LOCK).query().singleRow();
            long count = jdbc.sql("SELECT count(*) FROM simulation").query(Long.class).single();
            if (count >= max) {
                return false;
            }
        }
        insert(simulation);
        return true;
    }

    /** Inserts {@code simulation} as the featured one, unless another is already featured. */
    public void insertFeaturedIfMissing(Simulation simulation) {
        jdbc.sql("""
                INSERT INTO simulation (id, seed, scenario, current_tick, model_version, running, speed, featured)
                VALUES (?, ?, ?, ?, ?, ?, ?, true)
                ON CONFLICT (featured) WHERE featured DO NOTHING
                """)
                .params(simulation.id(), simulation.seed(), simulation.scenario(), simulation.currentTick(),
                        simulation.modelVersion(), simulation.running(), simulation.speed())
                .update();
    }

    public Optional<UUID> findFeaturedId() {
        return jdbc.sql("SELECT id FROM simulation WHERE featured").query(UUID.class).optional();
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

    /**
     * Deletes paused simulations created before {@code cutoff} with their telemetry and incidents.
     * The featured simulation is kept, since it cannot change.
     * The simulation rows are locked first, in the same order as a tick takes its locks, and rows a
     * tick is holding are skipped until the next purge. Must run inside a transaction.
     */
    public List<UUID> deleteCreatedBefore(Instant cutoff) {
        List<UUID> ids = jdbc.sql("""
                SELECT id FROM simulation WHERE created_at < ? AND NOT running AND NOT featured
                ORDER BY id FOR UPDATE SKIP LOCKED
                """)
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
