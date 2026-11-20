package com.nexus9.backend.simulation;

/** Published after an advance, run or pause has been committed. */
public record SimulationAdvanced(SimulationService.Progress progress) {
}
