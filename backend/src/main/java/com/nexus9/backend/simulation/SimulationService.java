package com.nexus9.backend.simulation;

import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.Station;
import com.nexus9.simulator.StationSnapshot;
import com.nexus9.simulator.scenario.Scenario;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SimulationService {

    public static final int MAX_TICKS_PER_REQUEST = 10_000;
    public static final int MAX_SPEED = 100;
    public static final int DEFAULT_SPEED = 5;

    private final SimulationRepository simulations;
    private final TelemetryRepository telemetry;
    private final IncidentService incidents;
    private final StationRegistry stations;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Limits limits;

    /**
     * Limits for a public demo, so anonymous users cannot fill the database. {@code maxTick} stops every
     * simulation at that tick and {@code maxSimulations} caps how many can exist; 0 means no limit.
     */
    public record Limits(long maxTick, int maxSimulations) {
    }

    public SimulationService(SimulationRepository simulations, TelemetryRepository telemetry,
            IncidentService incidents, StationRegistry stations, TransactionTemplate tx,
            ApplicationEventPublisher events, @Value("${nexus.max-tick:0}") long maxTick,
            @Value("${nexus.max-simulations:0}") int maxSimulations) {
        this.events = events;
        this.limits = new Limits(maxTick, maxSimulations);
        this.simulations = simulations;
        this.telemetry = telemetry;
        this.incidents = incidents;
        this.stations = stations;
        this.tx = tx;
    }

    /** State after an advance, read in the same transaction that committed its last tick. */
    public record Progress(Simulation simulation, int openIncidents) {
    }

    public Simulation create(long seed, String scenarioName) {
        Scenario.byName(scenarioName);
        Simulation simulation = new Simulation(UUID.randomUUID(), seed, scenarioName, 0, Station.MODEL_VERSION,
                false, DEFAULT_SPEED);
        if (!tx.execute(status -> simulations.insertIfFewerThan(simulation, limits.maxSimulations()))) {
            throw new DemoLimitException("this demo keeps at most " + limits.maxSimulations()
                    + " simulations; try again later");
        }
        return simulation;
    }

    public Simulation get(UUID id) {
        return simulations.findById(id).orElseThrow(() -> new SimulationNotFoundException(id));
    }

    /**
     * Advances {@code ticks} ticks from wherever the simulation is. Not idempotent: a retried request
     * advances again. Each tick commits on its own, so a failure can leave part of the request applied.
     */
    public Progress advance(UUID id, int ticks) {
        if (ticks < 1 || ticks > MAX_TICKS_PER_REQUEST) {
            throw new IllegalArgumentException("ticks must be between 1 and " + MAX_TICKS_PER_REQUEST);
        }
        checkTickLimit(get(id).currentTick() + ticks);
        Progress progress = null;
        for (int i = 0; i < ticks; i++) {
            progress = tx.execute(status -> step(id, Long.MAX_VALUE));
        }
        events.publishEvent(new SimulationAdvanced(progress));
        return progress;
    }

    /**
     * Advances until {@code currentTick == targetTick}. Idempotent: retrying the same request, or
     * sending it when the target was already reached, does nothing more.
     */
    public Progress advanceTo(UUID id, long targetTick) {
        if (targetTick < 0) {
            throw new IllegalArgumentException("toTick must not be negative");
        }
        if (targetTick - get(id).currentTick() > MAX_TICKS_PER_REQUEST) {
            throw new IllegalArgumentException("at most " + MAX_TICKS_PER_REQUEST + " ticks per request");
        }
        checkTickLimit(targetTick);
        while (true) {
            Progress progress = tx.execute(status -> step(id, targetTick));
            if (progress.simulation().currentTick() >= targetTick) {
                events.publishEvent(new SimulationAdvanced(progress));
                return progress;
            }
        }
    }

    /**
     * One step of the server clock: up to {@code speed} ticks, stopping as soon as the simulation is
     * paused, deleted or reaches the tick limit. Running state, speed and limit are checked under the
     * row lock before every tick, so a pause is never overtaken by a step that already started.
     */
    public void clockStep(UUID id) {
        Progress last = null;
        for (int done = 0; ; done++) {
            int alreadyDone = done;
            ClockTick tick = tx.execute(status -> {
                Simulation sim = simulations.findByIdForUpdate(id).orElse(null);
                if (sim == null || !sim.running() || alreadyDone >= sim.speed()) {
                    return null;
                }
                if (atTickLimit(sim.currentTick())) {
                    simulations.updateClock(id, false, sim.speed());
                    Simulation paused = new Simulation(sim.id(), sim.seed(), sim.scenario(), sim.currentTick(),
                            sim.modelVersion(), false, sim.speed());
                    return new ClockTick(new Progress(paused, incidents.countOpen(id)), false);
                }
                return new ClockTick(stepLocked(sim), true);
            });
            if (tick == null) {
                break;
            }
            last = tick.progress();
            if (!tick.more()) {
                break;
            }
        }
        if (last != null) {
            events.publishEvent(new SimulationAdvanced(last));
        }
    }

    private record ClockTick(Progress progress, boolean more) {
    }

    /** Lets the server clock advance the simulation {@code speed} ticks per step. */
    public Progress run(UUID id, int speed) {
        if (speed < 1 || speed > MAX_SPEED) {
            throw new IllegalArgumentException("speed must be between 1 and " + MAX_SPEED);
        }
        if (atTickLimit(get(id).currentTick())) {
            throw new DemoLimitException("this demo stops simulations at tick " + limits.maxTick());
        }
        return setClock(id, true, speed);
    }

    public Progress pause(UUID id) {
        return setClock(id, false, get(id).speed());
    }

    private Progress setClock(UUID id, boolean running, int speed) {
        Progress progress = tx.execute(status -> {
            Simulation sim = simulations.findByIdForUpdate(id)
                    .orElseThrow(() -> new SimulationNotFoundException(id));
            simulations.updateClock(id, running, speed);
            return new Progress(new Simulation(sim.id(), sim.seed(), sim.scenario(), sim.currentTick(),
                    sim.modelVersion(), running, speed), incidents.countOpen(id));
        });
        events.publishEvent(new SimulationAdvanced(progress));
        return progress;
    }

    /** Runs one tick inside a transaction, unless the simulation is already at {@code targetTick}. */
    private Progress step(UUID id, long targetTick) {
        Simulation simulation = simulations.findByIdForUpdate(id)
                .orElseThrow(() -> new SimulationNotFoundException(id));
        if (simulation.currentTick() >= targetTick) {
            return new Progress(simulation, incidents.countOpen(id));
        }
        if (atTickLimit(simulation.currentTick())) {
            throw new DemoLimitException("this demo stops simulations at tick " + limits.maxTick());
        }
        return stepLocked(simulation);
    }

    /** Runs one tick of a simulation whose row is already locked by the current transaction. */
    private Progress stepLocked(Simulation simulation) {
        UUID id = simulation.id();
        if (simulation.modelVersion() != Station.MODEL_VERSION) {
            throw new ModelVersionMismatchException(id, simulation.modelVersion(), Station.MODEL_VERSION);
        }
        Station station = stations.stationAt(simulation);
        try {
            StationSnapshot snapshot = station.advance();
            telemetry.append(id, snapshot);
            incidents.evaluate(id, snapshot);
            long next = snapshot.tick() + 1;
            simulations.updateCurrentTick(id, next);
            return new Progress(simulation.withCurrentTick(next), incidents.countOpen(id));
        } catch (RuntimeException e) {
            // The in-memory station is now ahead of the database; drop it so it gets rebuilt.
            stations.evict(id);
            throw e;
        }
    }

    private boolean atTickLimit(long tick) {
        return limits.maxTick() > 0 && tick >= limits.maxTick();
    }

    private void checkTickLimit(long targetTick) {
        if (limits.maxTick() > 0 && targetTick > limits.maxTick()) {
            throw new DemoLimitException("this demo stops simulations at tick " + limits.maxTick());
        }
    }
}
