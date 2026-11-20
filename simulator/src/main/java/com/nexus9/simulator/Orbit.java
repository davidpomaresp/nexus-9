package com.nexus9.simulator;

/**
 * Órbita de la estación: en cada vuelta hay una fase de luz y otra de sombra.
 *
 * <p>Una vuelta dura {@code periodTicks}; los primeros {@code sunlitTicks} son de luz y el resto
 * de sombra. Los valores por defecto (92 y 57) aproximan una órbita baja de unos 92 minutos.
 */
public record Orbit(int periodTicks, int sunlitTicks) {

    public static final Orbit LOW_EARTH_ORBIT = new Orbit(92, 57);

    public Orbit {
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks debe ser positivo: " + periodTicks);
        }
        if (sunlitTicks < 0 || sunlitTicks > periodTicks) {
            throw new IllegalArgumentException(
                    "sunlitTicks debe estar entre 0 y periodTicks: " + sunlitTicks);
        }
    }

    public boolean isSunlit(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick no puede ser negativo: " + tick);
        }
        return tick % periodTicks < sunlitTicks;
    }
}
