package com.nexus9.backend.api;

import com.nexus9.backend.domain.StationModule;
import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.incident.IncidentStatus;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.backend.simulation.SimulationService.Progress;
import com.nexus9.backend.telemetry.TelemetryRecord;
import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.scenario.Scenario;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private static final int MAX_READINGS = 5_000;

    private final SimulationService simulations;
    private final IncidentService incidents;
    private final TelemetryRepository telemetry;
    private final SimulationQueries queries;
    private final SimulationEvents events;

    SimulationController(SimulationService simulations, IncidentService incidents, TelemetryRepository telemetry,
            SimulationQueries queries, SimulationEvents events) {
        this.events = events;
        this.simulations = simulations;
        this.incidents = incidents;
        this.telemetry = telemetry;
        this.queries = queries;
    }

    @GetMapping("/scenarios")
    public List<String> scenarios() {
        return Scenario.BUILT_IN.stream().map(Scenario::name).toList();
    }

    @PostMapping("/simulations")
    public ResponseEntity<SimulationResponse> create(@Valid @RequestBody CreateSimulationRequest request) {
        Simulation sim = simulations.create(request.seed(), request.scenario());
        return ResponseEntity.created(URI.create("/api/simulations/" + sim.id()))
                .body(SimulationResponse.of(sim, 0));
    }

    @GetMapping("/simulations/{id}")
    public SimulationResponse get(@PathVariable UUID id) {
        return queries.summary(id);
    }

    /** {@code ticks} advances relative to the current tick; {@code toTick} is absolute and idempotent. */
    @PostMapping("/simulations/{id}/advance")
    public SimulationResponse advance(@PathVariable UUID id, @RequestParam Optional<Integer> ticks,
            @RequestParam Optional<Long> toTick) {
        if (ticks.isPresent() && toTick.isPresent()) {
            throw new IllegalArgumentException("use either ticks or toTick, not both");
        }
        return response(toTick.isPresent()
                ? simulations.advanceTo(id, toTick.get())
                : simulations.advance(id, ticks.orElse(1)));
    }

    private static SimulationResponse response(Progress progress) {
        return SimulationResponse.of(progress.simulation(), progress.openIncidents());
    }

    /** The server clock advances the simulation {@code speed} ticks every 250 ms until paused. */
    @PostMapping("/simulations/{id}/run")
    public SimulationResponse run(@PathVariable UUID id,
            @RequestParam(defaultValue = "" + SimulationService.DEFAULT_SPEED) int speed) {
        return response(simulations.run(id, speed));
    }

    @PostMapping("/simulations/{id}/pause")
    public SimulationResponse pause(@PathVariable UUID id) {
        return response(simulations.pause(id));
    }

    @GetMapping(path = "/simulations/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id) {
        return events.subscribe(queries.summary(id));
    }

    @GetMapping("/simulations/{id}/modules")
    public List<ModuleResponse> modules(@PathVariable UUID id) {
        return queries.modules(id);
    }

    /** Without {@code fromTick}, returns the last {@value #MAX_READINGS} readings up to {@code toTick}. */
    @GetMapping("/simulations/{id}/modules/{module}/readings")
    public List<TelemetryRecord> readings(@PathVariable UUID id, @PathVariable StationModule module,
            @RequestParam Optional<Long> fromTick, @RequestParam Optional<Long> toTick) {
        Simulation sim = simulations.get(id);
        if (fromTick.orElse(0L) < 0 || toTick.orElse(0L) < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        long to = toTick.orElse(sim.currentTick() - 1);
        long from = fromTick.orElse(Math.max(0, to - MAX_READINGS + 1));
        if (to < from) {
            if (fromTick.isPresent() && toTick.isPresent()) {
                throw new IllegalArgumentException("toTick must not be lower than fromTick");
            }
            return List.of();
        }
        if (to - from >= MAX_READINGS) {
            throw new IllegalArgumentException("at most " + MAX_READINGS + " readings per request");
        }
        return telemetry.find(id, module, from, to);
    }

    @GetMapping("/simulations/{id}/incidents")
    public List<Incident> incidents(@PathVariable UUID id, @RequestParam Optional<IncidentStatus> status) {
        simulations.get(id);
        return incidents.find(id, status);
    }
}
