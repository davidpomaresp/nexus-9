package com.nexus9.simulator;

import com.nexus9.simulator.comms.CommsReading;
import com.nexus9.simulator.oxygen.OxygenReading;
import com.nexus9.simulator.power.PowerReading;

public record StationSnapshot(long tick, PowerReading power, OxygenReading oxygen, CommsReading comms) {
}
