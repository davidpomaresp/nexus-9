package com.nexus9.backend.simulation;

import java.util.UUID;

public class ModelVersionMismatchException extends RuntimeException {

    public ModelVersionMismatchException(UUID id, int stored, int current) {
        super("simulation " + id + " was created with model version " + stored
                + " and cannot continue with version " + current);
    }
}
