package com.nexus9.backend.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.simulator.StationSnapshot;
import com.nexus9.simulator.comms.CommsMode;
import com.nexus9.simulator.comms.CommsReading;
import com.nexus9.simulator.oxygen.OxygenReading;
import com.nexus9.simulator.power.PowerReading;
import org.junit.jupiter.api.Test;

class RulesTest {

    @Test
    void batteryLowUsesHysteresis() {
        Rule rule = Rules.BATTERY_LOW;

        assertThat(rule.openWhen().test(battery(19))).isTrue();
        assertThat(rule.openWhen().test(battery(21))).isFalse();
        assertThat(rule.resolveWhen().test(battery(22))).isFalse();
        assertThat(rule.resolveWhen().test(battery(26))).isTrue();
    }

    @Test
    void generationDeficitIsIgnoredInEclipse() {
        assertThat(Rules.GENERATION_DEFICIT.openWhen().test(generation(false, 0))).isFalse();
        assertThat(Rules.GENERATION_DEFICIT.resolveWhen().test(generation(false, 0))).isFalse();
        assertThat(Rules.GENERATION_DEFICIT.openWhen().test(generation(true, 7.8))).isTrue();
        assertThat(Rules.GENERATION_DEFICIT.resolveWhen().test(generation(true, 12))).isTrue();
    }

    @Test
    void commsDegradedWhenNotInFullMode() {
        assertThat(Rules.COMMS_DEGRADED.openWhen().test(comms(CommsMode.MINIMAL))).isTrue();
        assertThat(Rules.COMMS_DEGRADED.resolveWhen().test(comms(CommsMode.FULL))).isTrue();
    }

    @Test
    void oxygenLowUsesHysteresis() {
        assertThat(Rules.OXYGEN_LOW.openWhen().test(oxygen(94.9))).isTrue();
        assertThat(Rules.OXYGEN_LOW.resolveWhen().test(oxygen(96))).isFalse();
        assertThat(Rules.OXYGEN_LOW.resolveWhen().test(oxygen(97.5))).isTrue();
    }

    private static StationSnapshot snapshot(PowerReading power, double oxygen, CommsMode comms) {
        return new StationSnapshot(0, power, new OxygenReading(0, true, oxygen), new CommsReading(0, comms));
    }

    private static StationSnapshot battery(double percent) {
        return snapshot(new PowerReading(0, true, 12, 6.5, 6.5, percent / 5, percent), 100, CommsMode.FULL);
    }

    private static StationSnapshot generation(boolean sunlit, double kw) {
        return snapshot(new PowerReading(0, sunlit, kw, 6.5, 6.5, 16, 80), 100, CommsMode.FULL);
    }

    private static StationSnapshot comms(CommsMode mode) {
        return snapshot(new PowerReading(0, true, 12, 6.5, 6.5, 16, 80), 100, mode);
    }

    private static StationSnapshot oxygen(double level) {
        return snapshot(new PowerReading(0, true, 12, 6.5, 6.5, 16, 80), level, CommsMode.FULL);
    }
}
