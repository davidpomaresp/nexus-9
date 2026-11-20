package com.nexus9.simulator.power;

/**
 * Panel solar. Con luz genera {@code nominalKw * efficiency}; en sombra, nada.
 *
 * <p>La eficiencia representa su salud: 1 = sano, 0 = averiado por completo. Las averías se
 * modelan bajando la eficiencia.
 */
public final class SolarPanel {

    private final String id;
    private final double nominalKw;
    private double efficiency = 1.0;

    public SolarPanel(String id, double nominalKw) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El panel necesita un id");
        }
        if (nominalKw <= 0) {
            throw new IllegalArgumentException("nominalKw debe ser positivo: " + nominalKw);
        }
        this.id = id;
        this.nominalKw = nominalKw;
    }

    /** Potencia generada en este tick antes de aplicar ruido. */
    public double outputKw(boolean sunlit) {
        return sunlit ? nominalKw * efficiency : 0.0;
    }

    public void setEfficiency(double efficiency) {
        if (efficiency < 0 || efficiency > 1) {
            throw new IllegalArgumentException("efficiency debe estar en [0, 1]: " + efficiency);
        }
        this.efficiency = efficiency;
    }

    public String id() {
        return id;
    }

    public double nominalKw() {
        return nominalKw;
    }

    public double efficiency() {
        return efficiency;
    }
}
