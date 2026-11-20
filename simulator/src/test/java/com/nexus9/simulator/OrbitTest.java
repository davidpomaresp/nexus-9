package com.nexus9.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrbitTest {

    private final Orbit orbit = Orbit.LOW_EARTH_ORBIT;

    @Test
    void firstPartOfEachOrbitIsSunlit() {
        assertThat(orbit.isSunlit(0)).isTrue();
        assertThat(orbit.isSunlit(56)).isTrue();
        assertThat(orbit.isSunlit(57)).isFalse();
        assertThat(orbit.isSunlit(91)).isFalse();
    }

    @Test
    void repeatsEveryPeriod() {
        assertThat(orbit.isSunlit(92)).isTrue();
        assertThat(orbit.isSunlit(92 + 57)).isFalse();
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new Orbit(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Orbit(10, 11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orbit.isSunlit(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
