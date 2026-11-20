package com.nexus9.simulator.power;

public final class Battery {

    private final double capacityKwh;
    private double chargeKwh;

    public Battery(double capacityKwh, double initialChargeKwh) {
        if (capacityKwh <= 0) {
            throw new IllegalArgumentException("capacityKwh must be positive");
        }
        if (initialChargeKwh < 0 || initialChargeKwh > capacityKwh) {
            throw new IllegalArgumentException("initial charge out of range");
        }
        this.capacityKwh = capacityKwh;
        this.chargeKwh = initialChargeKwh;
    }

    /** Returns the energy actually stored. */
    double charge(double energyKwh) {
        double stored = Math.min(energyKwh, capacityKwh - chargeKwh);
        chargeKwh += stored;
        return stored;
    }

    /** Returns the energy actually delivered, which may be less than requested. */
    double discharge(double energyKwh) {
        double delivered = Math.min(energyKwh, chargeKwh);
        chargeKwh -= delivered;
        return delivered;
    }

    public double capacityKwh() {
        return capacityKwh;
    }

    public double chargeKwh() {
        return chargeKwh;
    }

    public double chargePercent() {
        return 100.0 * chargeKwh / capacityKwh;
    }
}
