package com.nexus9.simulator.power;

import static com.nexus9.simulator.SimulationTime.HOURS_PER_TICK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nexus9.simulator.Orbit;
import com.nexus9.simulator.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class PowerSystemTest {

    private static final double DEMAND_KW = 6.5;
    private static final long DARK_TICK = 57;

    @Test
    void chargesBatteryWhenGenerationExceedsDemand() {
        PowerSystem power = PowerSystem.standard();
        double before = power.battery().chargeKwh();

        PowerReading r = power.step(0, DEMAND_KW, new RandomSource(1));

        assertThat(r.generationKw()).isBetween(12 * 0.98, 12 * 1.02);
        assertThat(r.servedKw()).isEqualTo(DEMAND_KW);
        assertThat(r.batteryKwh())
                .isCloseTo(before + (r.generationKw() - DEMAND_KW) * HOURS_PER_TICK, within(1e-9));
    }

    @Test
    void batteryCoversDemandDuringEclipse() {
        PowerSystem power = PowerSystem.standard();
        double before = power.battery().chargeKwh();

        PowerReading r = power.step(DARK_TICK, DEMAND_KW, new RandomSource(1));

        assertThat(r.generationKw()).isZero();
        assertThat(r.servedKw()).isCloseTo(DEMAND_KW, within(1e-9));
        assertThat(r.batteryKwh()).isCloseTo(before - DEMAND_KW * HOURS_PER_TICK, within(1e-9));
    }

    @Test
    void nothingIsServedWithEmptyBatteryInEclipse() {
        PowerSystem power = singlePanel(new Battery(20, 0));

        PowerReading r = power.step(DARK_TICK, DEMAND_KW, new RandomSource(1));

        assertThat(r.servedKw()).isZero();
        assertThat(r.unservedKw()).isEqualTo(DEMAND_KW);
    }

    @Test
    void servesOnlyWhatIsLeftInTheBattery() {
        PowerSystem power = singlePanel(new Battery(20, DEMAND_KW * HOURS_PER_TICK / 2));

        PowerReading r = power.step(DARK_TICK, DEMAND_KW, new RandomSource(1));

        assertThat(r.servedKw()).isCloseTo(DEMAND_KW / 2, within(1e-9));
        assertThat(r.batteryKwh()).isZero();
    }

    @Test
    void batteryDoesNotExceedCapacity() {
        PowerSystem power = singlePanel(new Battery(20, 20));

        PowerReading r = power.step(0, 0, new RandomSource(1));

        assertThat(r.batteryPercent()).isEqualTo(100);
    }

    @Test
    void degradedPanelReducesGeneration() {
        PowerSystem power = PowerSystem.standard();
        power.panel("SOLAR_PANEL_A").setEfficiency(0.3);

        PowerReading r = power.step(0, DEMAND_KW, new RandomSource(1));

        assertThat(r.generationKw()).isBetween(7.8 * 0.98, 7.8 * 1.02);
    }

    @Test
    void batteryRunsOutWhenBothPanelsFail() {
        PowerSystem power = PowerSystem.standard();
        power.panel("SOLAR_PANEL_A").setEfficiency(0);
        power.panel("SOLAR_PANEL_B").setEfficiency(0);
        RandomSource random = new RandomSource(1);

        PowerReading r = null;
        for (long tick = 0; tick < 200; tick++) {
            r = power.step(tick, DEMAND_KW, random);
        }

        assertThat(r.batteryKwh()).isZero();
        assertThat(r.servedKw()).isZero();
    }

    @Test
    void sameSeedGivesSameReadings() {
        assertThat(run(42, 1000)).isEqualTo(run(42, 1000));
    }

    @Test
    void differentSeedsGiveDifferentReadings() {
        assertThat(run(42, 1000)).isNotEqualTo(run(43, 1000));
    }

    @Test
    void rejectsInvalidValues() {
        PowerSystem power = PowerSystem.standard();
        assertThatThrownBy(() -> power.panel("SOLAR_PANEL_A").setEfficiency(1.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> power.panel("SOLAR_PANEL_A").setEfficiency(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> power.step(0, -1, new RandomSource(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> power.panel("X")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> new Battery(10, 11)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PowerSystem singlePanel(Battery battery) {
        return new PowerSystem(Orbit.LOW_EARTH_ORBIT, List.of(new SolarPanel("A", 6)), battery);
    }

    private static List<PowerReading> run(long seed, int ticks) {
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
