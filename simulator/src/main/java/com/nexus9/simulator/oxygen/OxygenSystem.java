package com.nexus9.simulator.oxygen;

/**
 * O2 level as a percentage of nominal. The generator only works when powered;
 * otherwise the crew keeps consuming and the level drops.
 */
public final class OxygenSystem {

    public static final double GENERATOR_KW = 3.0;
    static final double RECOVERY_PER_TICK = 0.5;
    static final double DEPLETION_PER_TICK = 0.25;

    private double levelPercent;

    public OxygenSystem() {
        this(100.0);
    }

    public OxygenSystem(double initialLevelPercent) {
        if (initialLevelPercent < 0 || initialLevelPercent > 100) {
            throw new IllegalArgumentException("level must be in [0, 100]");
        }
        this.levelPercent = initialLevelPercent;
    }

    public OxygenReading step(long tick, boolean powered) {
        if (powered) {
            levelPercent = Math.min(100.0, levelPercent + RECOVERY_PER_TICK);
        } else {
            levelPercent = Math.max(0.0, levelPercent - DEPLETION_PER_TICK);
        }
        return new OxygenReading(tick, powered, levelPercent);
    }

    public double levelPercent() {
        return levelPercent;
    }
}
