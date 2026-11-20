package com.nexus9.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrbitTest {

    private final Orbit orbit = Orbit.LOW_EARTH_ORBIT; // 92 ticks, 57 de luz

    @Test
    void losPrimeros57TicksDeCadaVueltaSonDeLuz() {
        assertThat(orbit.isSunlit(0)).isTrue();
        assertThat(orbit.isSunlit(56)).isTrue();
        assertThat(orbit.isSunlit(57)).isFalse();
        assertThat(orbit.isSunlit(91)).isFalse();
    }

    @Test
    void laOrbitaSeRepiteCadaPeriodo() {
        assertThat(orbit.isSunlit(92)).isTrue();
        assertThat(orbit.isSunlit(92 + 57)).isFalse();
        assertThat(orbit.isSunlit(92L * 1000 + 10)).isTrue();
    }

    @Test
    void rechazaConfiguracionesInvalidas() {
        assertThatThrownBy(() -> new Orbit(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Orbit(10, 11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orbit.isSunlit(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
