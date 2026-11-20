package com.nexus9.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RandomSourceTest {

    @Test
    void laMismaSemillaProduceLaMismaSecuencia() {
        RandomSource a = new RandomSource(42);
        RandomSource b = new RandomSource(42);

        for (int i = 0; i < 1_000; i++) {
            assertThat(a.noiseFactor(0.02)).isEqualTo(b.noiseFactor(0.02));
        }
    }

    @Test
    void elRuidoQuedaDentroDeLaAmplitud() {
        RandomSource random = new RandomSource(7);

        for (int i = 0; i < 10_000; i++) {
            assertThat(random.noiseFactor(0.02)).isBetween(0.98, 1.02);
        }
    }

    @Test
    void amplitudCeroNoIntroduceRuido() {
        assertThat(new RandomSource(1).noiseFactor(0)).isEqualTo(1.0);
    }
}
