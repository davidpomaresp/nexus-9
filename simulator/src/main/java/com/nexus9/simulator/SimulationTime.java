package com.nexus9.simulator;

/**
 * Unidad de tiempo de la simulación.
 *
 * <p>La simulación nunca consulta la hora real: avanza en ticks discretos (ADR 0003). Cada tick
 * representa {@value #SECONDS_PER_TICK} segundos de tiempo de estación.
 */
public final class SimulationTime {

    public static final int SECONDS_PER_TICK = 60;

    /** Duración de un tick en horas, para pasar de potencia (kW) a energía (kWh). */
    public static final double HOURS_PER_TICK = SECONDS_PER_TICK / 3600.0;

    private SimulationTime() {
    }
}
