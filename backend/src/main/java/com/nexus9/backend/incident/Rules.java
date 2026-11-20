package com.nexus9.backend.incident;

import com.nexus9.backend.simulation.StationModule;
import com.nexus9.simulator.comms.CommsMode;
import com.nexus9.simulator.power.PowerSystem;
import java.util.List;

public final class Rules {

    static final double NOMINAL_GENERATION_KW = PowerSystem.standard().nominalGenerationKw();

    public static final Rule BATTERY_LOW = new Rule("BATTERY_LOW", StationModule.POWER, Priority.HIGH,
            s -> s.power().batteryPercent() < 20,
            s -> s.power().batteryPercent() > 25);

    public static final Rule BATTERY_CRITICAL = new Rule("BATTERY_CRITICAL", StationModule.POWER, Priority.CRITICAL,
            s -> s.power().batteryPercent() < 5,
            s -> s.power().batteryPercent() > 10);

    // Only evaluated in sunlight: in eclipse zero generation is expected.
    public static final Rule GENERATION_DEFICIT = new Rule("GENERATION_DEFICIT", StationModule.POWER, Priority.MEDIUM,
            s -> s.power().sunlit() && s.power().generationKw() < 0.7 * NOMINAL_GENERATION_KW,
            s -> s.power().sunlit() && s.power().generationKw() >= 0.8 * NOMINAL_GENERATION_KW);

    public static final Rule COMMS_DEGRADED = new Rule("COMMS_DEGRADED", StationModule.COMMS, Priority.MEDIUM,
            s -> s.comms().mode() != CommsMode.FULL,
            s -> s.comms().mode() == CommsMode.FULL);

    public static final Rule OXYGEN_LOW = new Rule("OXYGEN_LOW", StationModule.OXYGEN, Priority.CRITICAL,
            s -> s.oxygen().levelPercent() < 95,
            s -> s.oxygen().levelPercent() > 97);

    public static final List<Rule> ALL = List.of(
            BATTERY_LOW, BATTERY_CRITICAL, GENERATION_DEFICIT, COMMS_DEGRADED, OXYGEN_LOW);

    private Rules() {
    }
}
