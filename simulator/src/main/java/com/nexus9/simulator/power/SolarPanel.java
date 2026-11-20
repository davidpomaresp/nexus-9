package com.nexus9.simulator.power;

public final class SolarPanel {

    private final String id;
    private final double nominalKw;
    private double efficiency = 1.0;

    public SolarPanel(String id, double nominalKw) {
        if (nominalKw <= 0) {
            throw new IllegalArgumentException("nominalKw must be positive");
        }
        this.id = id;
        this.nominalKw = nominalKw;
    }

    public double outputKw(boolean sunlit) {
        return sunlit ? nominalKw * efficiency : 0.0;
    }

    public void setEfficiency(double efficiency) {
        if (!(efficiency >= 0 && efficiency <= 1)) {
            throw new IllegalArgumentException("efficiency must be in [0, 1]");
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
