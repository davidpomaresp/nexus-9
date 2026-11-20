package com.nexus9.simulator;

import com.nexus9.simulator.comms.CommsMode;
import com.nexus9.simulator.oxygen.OxygenSystem;

/**
 * Which loads get power this tick. Priority: base load, then oxygen, then comms.
 */
public record LoadAllocation(boolean oxygenPowered, CommsMode commsMode) {

    public static final double BASE_LOAD_KW = 2.0;

    /** Below these battery levels comms is reduced to save energy, even if power is available. */
    static final double COMMS_MINIMAL_BELOW_PERCENT = 30.0;
    static final double COMMS_OFF_BELOW_PERCENT = 15.0;

    public static LoadAllocation decide(double availableKw, double batteryPercent) {
        double remaining = availableKw - BASE_LOAD_KW;

        boolean oxygen = remaining + SimulationTime.EPSILON_KW >= OxygenSystem.GENERATOR_KW;
        if (oxygen) {
            remaining -= OxygenSystem.GENERATOR_KW;
        }

        CommsMode comms = preferredCommsMode(batteryPercent);
        while (comms != CommsMode.OFF && comms.powerKw() > remaining + SimulationTime.EPSILON_KW) {
            comms = comms.downgrade();
        }
        return new LoadAllocation(oxygen, comms);
    }

    static CommsMode preferredCommsMode(double batteryPercent) {
        if (batteryPercent < COMMS_OFF_BELOW_PERCENT) {
            return CommsMode.OFF;
        }
        if (batteryPercent < COMMS_MINIMAL_BELOW_PERCENT) {
            return CommsMode.MINIMAL;
        }
        return CommsMode.FULL;
    }

    public double demandKw() {
        return BASE_LOAD_KW + (oxygenPowered ? OxygenSystem.GENERATOR_KW : 0) + commsMode.powerKw();
    }
}
