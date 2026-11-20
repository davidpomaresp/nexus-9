package com.nexus9.simulator.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nexus9.simulator.Orbit;
import com.nexus9.simulator.RandomSource;
import com.nexus9.simulator.SimulationTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PowerSystemTest {

    /** Demanda de referencia: base 2 kW + oxígeno 3 kW + comunicaciones 1,5 kW. */
    private static final double DEMAND_KW = 6.5;

    private static final long FIRST_DARK_TICK = 57;

    @Test
    void conLuzYPanelesSanosSeCubreLaDemandaYSeCargaLaBateria() {
        PowerSystem power = PowerSystem.standard();
        double before = power.battery().chargeKwh();

        PowerReading reading = power.step(0, DEMAND_KW, new RandomSource(1));

        assertThat(reading.sunlit()).isTrue();
        assertThat(reading.generationKw()).isBetween(12 * 0.98, 12 * 1.02);
        assertThat(reading.servedKw()).isEqualTo(DEMAND_KW);
        assertThat(reading.unservedKw()).isZero();
        double expectedKwh = (reading.generationKw() - DEMAND_KW) * SimulationTime.HOURS_PER_TICK;
        assertThat(reading.batteryKwh()).isCloseTo(before + expectedKwh, within(1e-9));
    }

    @Test
    void enSombraLaBateriaCubreLaDemanda() {
        PowerSystem power = PowerSystem.standard();
        double before = power.battery().chargeKwh();

        PowerReading reading = power.step(FIRST_DARK_TICK, DEMAND_KW, new RandomSource(1));

        assertThat(reading.sunlit()).isFalse();
        assertThat(reading.generationKw()).isZero();
        assertThat(reading.servedKw()).isCloseTo(DEMAND_KW, within(1e-9));
        assertThat(reading.batteryKwh())
                .isCloseTo(before - DEMAND_KW * SimulationTime.HOURS_PER_TICK, within(1e-9));
    }

    @Test
    void conLaBateriaVaciaYEnSombraNoSePuedeServirLaDemanda() {
        PowerSystem power = new PowerSystem(
                Orbit.LOW_EARTH_ORBIT, List.of(new SolarPanel("A", 6)), new Battery(20, 0));

        PowerReading reading = power.step(FIRST_DARK_TICK, DEMAND_KW, new RandomSource(1));

        assertThat(reading.servedKw()).isZero();
        assertThat(reading.unservedKw()).isEqualTo(DEMAND_KW);
        assertThat(reading.batteryKwh()).isZero();
    }

    @Test
    void laBateriaSeDescargaParcialmenteCuandoNoLlegaParaTodo() {
        // Queda energía para medio tick de demanda: se sirve la mitad.
        double halfTickKwh = DEMAND_KW * SimulationTime.HOURS_PER_TICK / 2;
        PowerSystem power = new PowerSystem(
                Orbit.LOW_EARTH_ORBIT, List.of(new SolarPanel("A", 6)), new Battery(20, halfTickKwh));

        PowerReading reading = power.step(FIRST_DARK_TICK, DEMAND_KW, new RandomSource(1));

        assertThat(reading.servedKw()).isCloseTo(DEMAND_KW / 2, within(1e-9));
        assertThat(reading.batteryKwh()).isZero();
    }

    @Test
    void laBateriaNoSuperaSuCapacidad() {
        PowerSystem power = new PowerSystem(
                Orbit.LOW_EARTH_ORBIT, List.of(new SolarPanel("A", 6)), new Battery(20, 20));

        PowerReading reading = power.step(0, 0, new RandomSource(1));

        assertThat(reading.batteryKwh()).isEqualTo(20);
        assertThat(reading.batteryPercent()).isEqualTo(100);
    }

    @Test
    void unPanelAveriadoReduceLaGeneracion() {
        PowerSystem power = PowerSystem.standard();
        power.panel("SOLAR_PANEL_A").setEfficiency(0.3);

        PowerReading reading = power.step(0, DEMAND_KW, new RandomSource(1));

        // 6 kW * 0,3 + 6 kW = 7,8 kW, con ±2 % de ruido.
        assertThat(reading.generationKw()).isBetween(7.8 * 0.98, 7.8 * 1.02);
    }

    @Test
    void conAmbosPanelesAveriadosLaBateriaSeAgotaYDejaDeServirse() {
        PowerSystem power = PowerSystem.standard();
        power.panel("SOLAR_PANEL_A").setEfficiency(0);
        power.panel("SOLAR_PANEL_B").setEfficiency(0);
        RandomSource random = new RandomSource(1);

        // 16 kWh / 6,5 kW ≈ 2,46 h ≈ 148 ticks.
        PowerReading reading = null;
        for (long tick = 0; tick < 200; tick++) {
            reading = power.step(tick, DEMAND_KW, random);
        }

        assertThat(reading.batteryKwh()).isZero();
        assertThat(reading.servedKw()).isZero();
    }

    @Test
    void mismaSemillaProduceLecturasIdenticas() {
        assertThat(simulate(42, 1_000)).isEqualTo(simulate(42, 1_000));
    }

    @Test
    void semillasDistintasProducenLecturasDistintas() {
        assertThat(simulate(42, 1_000)).isNotEqualTo(simulate(43, 1_000));
    }

    @Test
    void rechazaValoresInvalidos() {
        PowerSystem power = PowerSystem.standard();
        assertThatThrownBy(() -> power.panel("SOLAR_PANEL_A").setEfficiency(1.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> power.step(0, -1, new RandomSource(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> power.panel("NO_EXISTE"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(() -> new Battery(10, 11)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Simula con una avería a mitad para que la secuencia sea representativa. */
    private static List<PowerReading> simulate(long seed, int ticks) {
        PowerSystem power = PowerSystem.standard();
        RandomSource random = new RandomSource(seed);
        List<PowerReading> readings = new ArrayList<>();
        for (long tick = 0; tick < ticks; tick++) {
            if (tick == 120) {
                power.panel("SOLAR_PANEL_A").setEfficiency(0.3);
            }
            readings.add(power.step(tick, DEMAND_KW, random));
        }
        return readings;
    }
}
