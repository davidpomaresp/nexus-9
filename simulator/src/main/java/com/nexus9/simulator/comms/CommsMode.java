package com.nexus9.simulator.comms;

public enum CommsMode {
    FULL(1.5),
    MINIMAL(0.4),
    OFF(0.0);

    private final double powerKw;

    CommsMode(double powerKw) {
        this.powerKw = powerKw;
    }

    public double powerKw() {
        return powerKw;
    }

    public CommsMode downgrade() {
        return this == FULL ? MINIMAL : OFF;
    }
}
