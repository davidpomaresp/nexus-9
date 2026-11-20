package com.nexus9.backend.simulation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SimulationRepository {

    private final JdbcClient jdbc;

    public SimulationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Simulation simulation) {
        jdbc.sql("INSERT INTO simulation (id, seed, scenario, current_tick) VALUES (?, ?, ?, ?)")
                .params(simulation.id(), simulation.seed(), simulation.scenario(), simulation.currentTick())
                .update();
    }

    public Optional<Simulation> findById(UUID id) {
        return jdbc.sql("SELECT id, seed, scenario, current_tick FROM simulation WHERE id = ?")
                .param(id)
                .query(Simulation.class)
                .optional();
    }

    /** Locks the row until the end of the transaction, so ticks of one simulation never run in parallel. */
    public Optional<Simulation> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT id, seed, scenario, current_tick FROM simulation WHERE id = ? FOR UPDATE")
                .param(id)
                .query(Simulation.class)
                .optional();
    }

    public void updateCurrentTick(UUID id, long currentTick) {
        jdbc.sql("UPDATE simulation SET current_tick = ? WHERE id = ?")
                .params(currentTick, id)
                .update();
    }
}
