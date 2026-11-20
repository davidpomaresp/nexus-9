package com.nexus9.backend.api;

import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.incident.IncidentStatus;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.backend.simulation.StationModule;
import com.nexus9.backend.telemetry.TelemetryRecord;
import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.scenario.Scenario;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private static final int MAX_READINGS = 5_000;

    private final SimulationService simulations;
    private final IncidentService incidents;
    private final TelemetryRepository telemetry;

    public SimulationController(SimulationService simulations, IncidentService incidents,
            TelemetryRepository telemetry) {
        this.simulations = simulations;
        this.incidents = incidents;
        this.telemetry = telemetry;
    }

    @GetMapping("/scenarios")
    public List<String> scenarios() {
        return List.of(Scenario.NOMINAL.name(), Scenario.SOLAR_DEGRADATION.name());
    }

    @PostMapping("/simulations")
    public ResponseEntity<SimulationResponse> create(@Valid @RequestBody CreateSimulationRequest request) {
        Simulation sim = simulations.create(request.seed(), request.scenario());
        return ResponseEntity.created(URI.create("/api/simulations/" + sim.id()))
                .body(SimulationResponse.of(sim, 0));
    }

    @GetMapping("/simulations/{id}")
    public SimulationResponse get(@PathVariable UUID id) {
        return response(simulations.get(id));
    }

    @PostMapping("/simulations/{id}/advance")
    public SimulationResponse advance(@PathVariable UUID id, @RequestParam(defaultValue = "1") int ticks) {
        return response(simulations.advance(id, ticks));
    }

    @GetMapping("/simulations/{id}/modules")
    public List<ModuleResponse> modules(@PathVariable UUID id) {
        simulations.get(id);
        List<Incident> open = incidents.find(id, Optional.of(IncidentStatus.OPEN));
        return Arrays.stream(StationModule.values())
                .map(module -> {
                    Optional<TelemetryRecord> latest = telemetry.latest(id, module);
                    Map<String, Object> values = latest.map(TelemetryRecord::values).orElse(Map.of());
                    return new ModuleResponse(module, ModuleStatusResolver.resolve(module, values, open),
                            latest.map(TelemetryRecord::tick).orElse(null), values);
                })
                .toList();
    }

    @GetMapping("/simulations/{id}/modules/{module}/readings")
    public List<TelemetryRecord> readings(@PathVariable UUID id, @PathVariable StationModule module,
            @RequestParam(defaultValue = "0") long fromTick, @RequestParam Optional<Long> toTick) {
        Simulation sim = simulations.get(id);
        long to = toTick.orElse(sim.currentTick() - 1);
        if (fromTick < 0 || to < fromTick || to - fromTick >= MAX_READINGS) {
            throw new IllegalArgumentException(
                    "invalid tick range; at most " + MAX_READINGS + " readings per request");
        }
        return telemetry.find(id, module, fromTick, to);
    }

    @GetMapping("/simulations/{id}/incidents")
    public List<Incident> incidents(@PathVariable UUID id, @RequestParam Optional<IncidentStatus> status) {
        simulations.get(id);
        return incidents.find(id, status);
    }

    private SimulationResponse response(Simulation sim) {
        return SimulationResponse.of(sim, incidents.find(sim.id(), Optional.of(IncidentStatus.OPEN)).size());
    }
}
