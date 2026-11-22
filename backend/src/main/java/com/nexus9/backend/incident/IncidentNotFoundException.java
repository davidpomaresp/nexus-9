package com.nexus9.backend.incident;

import java.util.UUID;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID id) {
        super("Incident not found: " + id);
    }
}
