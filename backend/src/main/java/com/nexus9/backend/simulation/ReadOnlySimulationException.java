package com.nexus9.backend.simulation;

import java.util.UUID;

public class ReadOnlySimulationException extends RuntimeException {

    public ReadOnlySimulationException(UUID id) {
        super("simulation " + id + " is the shared example and cannot be changed; create your own");
    }
}
