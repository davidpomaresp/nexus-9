package com.nexus9.simulator.oxygen;

public record OxygenReading(long tick, boolean generatorPowered, double levelPercent) {
}
