package com.nexus9.simulator.oxygen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OxygenSystemTest {

    @Test
    void levelDropsWithoutPower() {
        OxygenSystem oxygen = new OxygenSystem();

        oxygen.step(0, false);
        oxygen.step(1, false);

        assertThat(oxygen.levelPercent()).isEqualTo(99.5);
    }

    @Test
    void levelRecoversWithPowerUpToNominal() {
        OxygenSystem oxygen = new OxygenSystem(99.0);

        oxygen.step(0, true);
        assertThat(oxygen.levelPercent()).isEqualTo(99.5);

        for (int i = 0; i < 10; i++) {
            oxygen.step(i, true);
        }
        assertThat(oxygen.levelPercent()).isEqualTo(100.0);
    }

    @Test
    void levelNeverGoesBelowZero() {
        OxygenSystem oxygen = new OxygenSystem(0.1);

        OxygenReading r = oxygen.step(0, false);

        assertThat(r.levelPercent()).isZero();
    }
}
