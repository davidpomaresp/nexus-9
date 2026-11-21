package com.nexus9.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.simulator.comms.CommsMode;
import com.nexus9.simulator.scenario.PanelFault;
import com.nexus9.simulator.scenario.Scenario;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
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

    @Test
    void oxygenLeakDrainsAndRecoversAfterTheRepair() {
        List<StationSnapshot> run = run(42, Scenario.OXYGEN_LEAK, 600);

        assertThat(run.get(149).oxygen().levelPercent()).isEqualTo(100.0);
        assertThat(run.get(299).oxygen().levelPercent()).isLessThan(70);
        assertThat(run).allMatch(s -> s.oxygen().generatorPowered());
        assertThat(run.getLast().oxygen().levelPercent()).isEqualTo(100.0);
    }

    @Test
    void solarStormDrainsTheBatteryAndTheStationRecovers() {
        List<StationSnapshot> run = run(42, Scenario.SOLAR_STORM, 900);

        double lowest = run.stream().mapToDouble(s -> s.power().batteryPercent()).min().orElseThrow();
        long commsOff = firstTick(run, s -> s.comms().mode() == CommsMode.OFF);

        assertThat(lowest).isBetween(5.0, 15.0);
        assertThat(commsOff).isBetween(420L, 480L);
        assertThat(run).allMatch(s -> s.oxygen().generatorPowered());
        assertThat(run.getLast().comms().mode()).isEqualTo(CommsMode.FULL);
    }

    @Test
    void unknownPanelIsRejectedBeforeAnyTick() {
        Scenario broken = new Scenario("broken", List.of(new PanelFault(3, "SOLAR_PANEL_Z", 0.5)));

        assertThatThrownBy(() -> new Station(1, broken)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void matchesReferenceSequenceForSeed42() {
        List<StationSnapshot> run = run(42, Scenario.SOLAR_DEGRADATION, 421);

        StationSnapshot sunlit = run.get(0);
        assertThat(sunlit.power().sunlit()).isTrue();
        assertThat(sunlit.power().generationKw()).isEqualTo(11.976354065195697);
        assertThat(sunlit.power().batteryKwh()).isEqualTo(16.09127256775326);

        StationSnapshot eclipse = run.get(70);
        assertThat(eclipse.power().sunlit()).isFalse();
        assertThat(eclipse.power().generationKw()).isZero();
        assertThat(eclipse.power().batteryKwh()).isEqualTo(18.48333333333332);

        StationSnapshot afterFirstFault = run.get(121);
        assertThat(afterFirstFault.power().generationKw()).isEqualTo(7.771619011313256);
        assertThat(afterFirstFault.power().batteryKwh()).isEqualTo(18.824903526716255);

        assertThat(run.get(250).power().batteryKwh()).isEqualTo(11.681817859421207);

        StationSnapshot drained = run.get(420);
        assertThat(drained.power().servedKw()).isEqualTo(1.829085477871413);
        assertThat(drained.power().batteryPercent()).isZero();
        assertThat(drained.oxygen().generatorPowered()).isFalse();
        assertThat(drained.oxygen().levelPercent()).isEqualTo(95.0);
        assertThat(drained.comms().mode()).isEqualTo(CommsMode.OFF);
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
