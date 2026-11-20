package com.nexus9.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.simulator.comms.CommsMode;
import org.junit.jupiter.api.Test;

class LoadAllocationTest {

    @Test
    void everythingRunsWithEnoughPowerAndCharge() {
        LoadAllocation loads = LoadAllocation.decide(12, 80);

        assertThat(loads.oxygenPowered()).isTrue();
        assertThat(loads.commsMode()).isEqualTo(CommsMode.FULL);
        assertThat(loads.demandKw()).isEqualTo(6.5);
    }

    @Test
    void commsSavesEnergyWhenBatteryIsLow() {
        assertThat(LoadAllocation.decide(100, 29).commsMode()).isEqualTo(CommsMode.MINIMAL);
        assertThat(LoadAllocation.decide(100, 14).commsMode()).isEqualTo(CommsMode.OFF);
    }

    @Test
    void commsIsDowngradedToFitAvailablePower() {
        LoadAllocation loads = LoadAllocation.decide(5.5, 80);

        assertThat(loads.oxygenPowered()).isTrue();
        assertThat(loads.commsMode()).isEqualTo(CommsMode.MINIMAL);
    }

    @Test
    void oxygenIsDroppedOnlyWhenItCannotBePowered() {
        LoadAllocation loads = LoadAllocation.decide(4.9, 80);

        assertThat(loads.oxygenPowered()).isFalse();
        assertThat(loads.commsMode()).isEqualTo(CommsMode.FULL);
    }

    @Test
    void baseLoadIsAlwaysRequested() {
        LoadAllocation loads = LoadAllocation.decide(0, 0);

        assertThat(loads.oxygenPowered()).isFalse();
        assertThat(loads.commsMode()).isEqualTo(CommsMode.OFF);
        assertThat(loads.demandKw()).isEqualTo(LoadAllocation.BASE_LOAD_KW);
    }
}
