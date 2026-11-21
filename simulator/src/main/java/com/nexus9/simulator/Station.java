package com.nexus9.simulator;

import com.nexus9.simulator.comms.CommsReading;
import com.nexus9.simulator.oxygen.OxygenReading;
import com.nexus9.simulator.oxygen.OxygenSystem;
import com.nexus9.simulator.power.PowerReading;
import com.nexus9.simulator.power.PowerSystem;
import com.nexus9.simulator.scenario.Fault;
import com.nexus9.simulator.scenario.OxygenLeak;
import com.nexus9.simulator.scenario.PanelFault;
import com.nexus9.simulator.scenario.Scenario;

/**
 * Whole-station simulation. Same seed and scenario always produce the same snapshots.
 */
public final class Station {

    /**
     * Bump whenever a change alters the output for an existing seed and scenario. Stored simulations
     * are rebuilt by replay, so they can only continue with the model version that created them.
     */
    public static final int MODEL_VERSION = 2;

    private final long seed;
    private final Scenario scenario;
    private final RandomSource random;
    private final PowerSystem power = PowerSystem.standard();
    private final OxygenSystem oxygen = new OxygenSystem();
    private long nextTick = 0;

    public Station(long seed, Scenario scenario) {
        this.seed = seed;
        this.scenario = scenario;
        this.random = new RandomSource(seed);
        for (Fault fault : scenario.faults()) {
            if (fault instanceof PanelFault panelFault) {
                power.panel(panelFault.panelId());
            }
        }
    }

    public StationSnapshot advance() {
        long tick = nextTick;

        for (Fault fault : scenario.faultsAt(tick)) {
            switch (fault) {
                case PanelFault f -> power.panel(f.panelId()).setEfficiency(f.efficiency());
                case OxygenLeak f -> oxygen.setLeakPerTick(f.percentPerTick());
            }
        }

        double generationKw = power.generate(tick, random);
        LoadAllocation loads = LoadAllocation.decide(
                power.availableKw(generationKw), power.battery().chargePercent());

        PowerReading powerReading = power.settle(tick, generationKw, loads.demandKw());
        OxygenReading oxygenReading = oxygen.step(tick, loads.oxygenPowered());
        CommsReading commsReading = new CommsReading(tick, loads.commsMode());

        nextTick++;
        return new StationSnapshot(tick, powerReading, oxygenReading, commsReading);
    }

    public long seed() {
        return seed;
    }

    public Scenario scenario() {
        return scenario;
    }

    public long nextTick() {
        return nextTick;
    }
}
