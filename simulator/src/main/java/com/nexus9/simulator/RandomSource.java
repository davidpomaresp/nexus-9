package com.nexus9.simulator;

import java.util.SplittableRandom;

/**
 * Única fuente de aleatoriedad de la simulación.
 *
 * <p>Se crea siempre a partir de una semilla explícita, de modo que la misma semilla produce
 * exactamente la misma secuencia de valores (ADR 0003). Ninguna otra clase del simulador puede
 * usar {@code Math.random()} ni {@code new Random()}.
 */
public final class RandomSource {

    private final SplittableRandom random;

    public RandomSource(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /**
     * Factor multiplicativo de ruido uniforme en {@code [1 - amplitude, 1 + amplitude)}.
     * Por ejemplo, {@code noiseFactor(0.02)} devuelve un valor entre 0,98 y 1,02.
     */
    public double noiseFactor(double amplitude) {
        if (amplitude < 0 || amplitude >= 1) {
            throw new IllegalArgumentException("amplitude debe estar en [0, 1): " + amplitude);
        }
        if (amplitude == 0) {
            return 1.0;
        }
        return random.nextDouble(1 - amplitude, 1 + amplitude);
    }
}
