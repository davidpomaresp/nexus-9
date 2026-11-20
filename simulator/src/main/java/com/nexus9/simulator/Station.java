package com.nexus9.simulator;

import com.nexus9.simulator.comms.CommsReading;
import com.nexus9.simulator.oxygen.OxygenReading;
import com.nexus9.simulator.oxygen.OxygenSystem;
import com.nexus9.simulator.power.PowerReading;
import com.nexus9.simulator.power.PowerSystem;
import com.nexus9.simulator.scenario.PanelFault;
import com.nexus9.simulator.scenario.Scenario;

/**
 * Whole-station simulation. Same seed and scenario always produce the same snapshots.
 */
public final class Station {

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
    }

    public StationSnapshot advance() {
        long tick = nextTick++;

        for (PanelFault fault : scenario.faultsAt(tick)) {
            power.panel(fault.panelId()).setEfficiency(fault.efficiency());
        }

        double generationKw = power.generate(tick, random);
        LoadAllocation loads = LoadAllocation.decide(
                power.availableKw(generationKw), power.battery().chargePercent());

        PowerReading powerReading = power.settle(tick, generationKw, loads.demandKw());
        OxygenReading oxygenReading = oxygen.step(tick, loads.oxygenPowered());
        CommsReading commsReading = new CommsReading(tick, loads.commsMode());

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
