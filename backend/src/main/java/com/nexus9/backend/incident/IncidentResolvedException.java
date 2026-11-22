package com.nexus9.backend.incident;

import java.util.UUID;

public class IncidentResolvedException extends RuntimeException {

    public IncidentResolvedException(UUID id) {
        super("Incident already resolved without acknowledgement: " + id);
    }
}
