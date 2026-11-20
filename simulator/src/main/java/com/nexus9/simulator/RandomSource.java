package com.nexus9.simulator;

import java.util.SplittableRandom;

/**
 * Seeded source of randomness. The simulator must not use any other one,
 * otherwise runs are no longer reproducible.
 */
public final class RandomSource {

    private final SplittableRandom random;

    public RandomSource(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /** Returns a value in [1 - amplitude, 1 + amplitude). */
    public double noiseFactor(double amplitude) {
        if (amplitude < 0 || amplitude >= 1) {
            throw new IllegalArgumentException("amplitude must be in [0, 1)");
        }
        if (amplitude == 0) {
            return 1.0;
        }
        return random.nextDouble(1 - amplitude, 1 + amplitude);
    }
}
