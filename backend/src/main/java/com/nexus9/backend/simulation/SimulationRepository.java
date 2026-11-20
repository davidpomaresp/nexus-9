package com.nexus9.backend.simulation;

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

    public void updateCurrentTick(UUID id, long currentTick) {
        jdbc.sql("UPDATE simulation SET current_tick = ? WHERE id = ?")
                .params(currentTick, id)
                .update();
    }
}
