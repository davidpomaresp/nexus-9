package com.nexus9.backend.simulation;

import com.nexus9.simulator.Station;
import com.nexus9.simulator.scenario.Scenario;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory stations. They are a cache: the database is the source of truth. Since the simulator
 * is deterministic, a missing or out-of-sync station is rebuilt by replaying from tick 0.
 */
@Component
public class StationRegistry {

    private final Map<UUID, Station> stations = new ConcurrentHashMap<>();

    public Station stationAt(Simulation simulation) {
        Station station = stations.get(simulation.id());
        if (station == null || station.nextTick() != simulation.currentTick()) {
            station = rebuild(simulation);
            stations.put(simulation.id(), station);
        }
        return station;
    }

    public void evict(UUID id) {
        stations.remove(id);
    }

    public void clear() {
        stations.clear();
    }

    private static Station rebuild(Simulation simulation) {
        Station station = new Station(simulation.seed(), Scenario.byName(simulation.scenario()));
        while (station.nextTick() < simulation.currentTick()) {
            station.advance();
        }
        return station;
    }
}
