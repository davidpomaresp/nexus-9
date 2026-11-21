package com.nexus9.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.incident.IncidentStatus;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.backend.telemetry.TelemetryRecord;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SimulationQueriesTest extends PostgresTestBase {

    @Autowired
    SimulationService simulations;

    @Autowired
    SimulationQueries queries;

    @Test
    void stateNeverMixesTicksWhileTheSimulationAdvances() {
        UUID id = simulations.create(42, "solar-degradation").id();
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 300; i++) {
                simulations.advance(id, 1);
            }
        });

        int reads = 0;
        while (!writer.isDone() || reads == 0) {
            assertConsistent(queries.state(id, 0, 5_000));
            reads++;
        }
        writer.join();
        assertConsistent(queries.state(id, 0, 5_000));
    }

    @Test
    void stateCapsTheReadingsToTheMostRecentTicks() {
        Simulation sim = simulations.create(42, "nominal");
        simulations.advance(sim.id(), 50);

        List<TelemetryRecord> readings = queries.state(sim.id(), 0, 10).readings();

        assertThat(readings).hasSize(30);
        assertThat(readings.getFirst().tick()).isEqualTo(40);
        assertThat(readings.getLast().tick()).isEqualTo(49);
    }

    private static void assertConsistent(StateResponse state) {
        long last = state.simulation().currentTick() - 1;
        if (last >= 0) {
            assertThat(state.modules()).allSatisfy(m -> assertThat(m.tick()).isEqualTo(last));
            assertThat(state.readings().getLast().tick()).isEqualTo(last);
        }
        assertThat(state.readings()).hasSize((int) (last + 1) * 3);
        assertThat(state.incidents()).allSatisfy(i -> assertThat(i.openedAtTick()).isLessThanOrEqualTo(last));
        long open = state.incidents().stream().filter(i -> i.status() == IncidentStatus.OPEN).count();
        assertThat(state.simulation().openIncidents()).isEqualTo(open);
    }
}
