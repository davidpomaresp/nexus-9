package com.nexus9.backend.simulation;

/** A request that would go past one of the limits of the public demo. */
public class DemoLimitException extends RuntimeException {

    public DemoLimitException(String message) {
        super(message);
    }
}
