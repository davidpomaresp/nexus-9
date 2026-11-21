package com.nexus9.simulator.scenario;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public record Scenario(String name, List<Fault> faults) {

    public static final Scenario NOMINAL = new Scenario("nominal", List.of());

    public static final Scenario SOLAR_DEGRADATION = new Scenario("solar-degradation", List.of(
            new PanelFault(120, "SOLAR_PANEL_A", 0.3),
            new PanelFault(200, "SOLAR_PANEL_B", 0.0)));

    /** A hull leak at tick 150 that outpaces the O2 generator until it is repaired at tick 300. */
    public static final Scenario OXYGEN_LEAK = new Scenario("oxygen-leak", List.of(
            new OxygenLeak(150, 0.75),
            new OxygenLeak(300, 0.0)));

    /** A solar storm leaves both panels at 40 % between ticks 180 and 420, then they recover. */
    public static final Scenario SOLAR_STORM = new Scenario("solar-storm", List.of(
            new PanelFault(180, "SOLAR_PANEL_A", 0.4),
            new PanelFault(180, "SOLAR_PANEL_B", 0.4),
            new PanelFault(420, "SOLAR_PANEL_A", 1.0),
            new PanelFault(420, "SOLAR_PANEL_B", 1.0)));

    public static final List<Scenario> BUILT_IN = List.of(NOMINAL, SOLAR_DEGRADATION, OXYGEN_LEAK, SOLAR_STORM);

    public Scenario {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        faults = faults.stream().sorted(Comparator.comparingLong(Fault::tick)).toList();
    }

    public static Scenario byName(String name) {
        return BUILT_IN.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("unknown scenario: " + name));
    }

    public List<Fault> faultsAt(long tick) {
        return faults.stream().filter(f -> f.tick() == tick).toList();
    }
}
