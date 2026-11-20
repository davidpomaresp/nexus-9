package com.nexus9.simulator.power;

/** Batería de la estación: almacena energía entre 0 y su capacidad. */
public final class Battery {

    private final double capacityKwh;
    private double chargeKwh;

    public Battery(double capacityKwh, double initialChargeKwh) {
        if (capacityKwh <= 0) {
            throw new IllegalArgumentException("capacityKwh debe ser positiva: " + capacityKwh);
        }
        if (initialChargeKwh < 0 || initialChargeKwh > capacityKwh) {
            throw new IllegalArgumentException(
                    "La carga inicial debe estar entre 0 y la capacidad: " + initialChargeKwh);
        }
        this.capacityKwh = capacityKwh;
        this.chargeKwh = initialChargeKwh;
    }

    /** Guarda energía hasta llenarse. Devuelve la energía que realmente ha entrado. */
    double charge(double energyKwh) {
        double stored = Math.min(energyKwh, capacityKwh - chargeKwh);
        chargeKwh += stored;
        return stored;
    }

    /** Entrega energía hasta vaciarse. Devuelve la energía que realmente ha salido. */
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
