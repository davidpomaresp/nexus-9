package com.nexus9.simulator.scenario;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public record Scenario(String name, List<PanelFault> faults) {

    public static final Scenario NOMINAL = new Scenario("nominal", List.of());

    public static final Scenario SOLAR_DEGRADATION = new Scenario("solar-degradation", List.of(
            new PanelFault(120, "SOLAR_PANEL_A", 0.3),
            new PanelFault(200, "SOLAR_PANEL_B", 0.0)));

    private static final List<Scenario> BUILT_IN = List.of(NOMINAL, SOLAR_DEGRADATION);

    public Scenario {
        faults = faults.stream().sorted(Comparator.comparingLong(PanelFault::tick)).toList();
    }

    public static Scenario byName(String name) {
        return BUILT_IN.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("unknown scenario: " + name));
    }

    public List<PanelFault> faultsAt(long tick) {
        return faults.stream().filter(f -> f.tick() == tick).toList();
    }
}
