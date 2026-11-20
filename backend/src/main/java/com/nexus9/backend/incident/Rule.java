package com.nexus9.backend.incident;

import com.nexus9.backend.domain.StationModule;
import com.nexus9.simulator.StationSnapshot;
import java.util.function.Predicate;

/**
 * A rule opens an incident when {@code openWhen} holds and resolves it when {@code resolveWhen}
 * holds. Using two different thresholds (hysteresis) avoids incidents that flap open/closed.
 */
public record Rule(
        String code,
        StationModule module,
        Priority priority,
        Predicate<StationSnapshot> openWhen,
        Predicate<StationSnapshot> resolveWhen) {
}
