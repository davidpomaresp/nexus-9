package com.nexus9.simulator.scenario;

public record PanelFault(long tick, String panelId, double efficiency) {

    public PanelFault {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (panelId == null || panelId.isBlank()) {
            throw new IllegalArgumentException("panelId is required");
        }
        if (!(efficiency >= 0 && efficiency <= 1)) {
            throw new IllegalArgumentException("efficiency must be in [0, 1]");
        }
    }
}
