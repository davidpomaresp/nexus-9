package com.nexus9.simulator.power;

public record PowerReading(
        long tick,
        boolean sunlit,
        double generationKw,
        double demandKw,
        double servedKw,
        double batteryKwh,
        double batteryPercent) {

    public double unservedKw() {
        return demandKw - servedKw;
    }
}
