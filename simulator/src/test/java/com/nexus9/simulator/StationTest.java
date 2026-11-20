package com.nexus9.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.simulator.comms.CommsMode;
import com.nexus9.simulator.scenario.Scenario;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class StationTest {

    @Test
    void nominalStationStaysHealthy() {
        List<StationSnapshot> run = run(42, Scenario.NOMINAL, 1000);

        assertThat(run).allMatch(s -> s.comms().mode() == CommsMode.FULL);
        assertThat(run).allMatch(s -> s.oxygen().levelPercent() == 100.0);
        assertThat(run).allMatch(s -> s.power().unservedKw() == 0);
    }

    @Test
    void solarDegradationCascadesFromPowerToCommsToOxygen() {
        List<StationSnapshot> run = run(42, Scenario.SOLAR_DEGRADATION, 900);

        long commsMinimal = firstTick(run, s -> s.comms().mode() == CommsMode.MINIMAL);
        long commsOff = firstTick(run, s -> s.comms().mode() == CommsMode.OFF);
        long oxygenUnpowered = firstTick(run, s -> !s.oxygen().generatorPowered());
        long oxygenLow = firstTick(run, s -> s.oxygen().levelPercent() < 95);

        assertThat(commsMinimal).isGreaterThan(200);
        assertThat(commsMinimal).isLessThan(commsOff);
        assertThat(commsOff).isLessThan(oxygenUnpowered);
        assertThat(oxygenUnpowered).isLessThan(oxygenLow);
        assertThat(run.getLast().oxygen().levelPercent()).isLessThan(95);
    }

    @Test
    void sameSeedAndScenarioGiveSameSnapshots() {
        assertThat(run(7, Scenario.SOLAR_DEGRADATION, 900))
                .isEqualTo(run(7, Scenario.SOLAR_DEGRADATION, 900));
    }

    @Test
    void ticksAdvanceOneByOne() {
        Station station = new Station(1, Scenario.NOMINAL);

        assertThat(station.advance().tick()).isZero();
        assertThat(station.advance().tick()).isEqualTo(1);
        assertThat(station.nextTick()).isEqualTo(2);
    }

    private static List<StationSnapshot> run(long seed, Scenario scenario, int ticks) {
        Station station = new Station(seed, scenario);
        List<StationSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            snapshots.add(station.advance());
        }
        return snapshots;
    }

    private static long firstTick(List<StationSnapshot> run, Predicate<StationSnapshot> condition) {
        return run.stream().filter(condition).findFirst().orElseThrow().tick();
    }
}
