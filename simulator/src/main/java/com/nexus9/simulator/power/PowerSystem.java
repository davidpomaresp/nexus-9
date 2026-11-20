package com.nexus9.simulator.power;

import com.nexus9.simulator.Orbit;
import com.nexus9.simulator.RandomSource;
import com.nexus9.simulator.SimulationTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Sistema de energía: paneles solares, batería y balance energético.
 *
 * <p>En cada tick:
 * <ol>
 *   <li>Los paneles generan según la órbita (luz/sombra), su eficiencia y un ruido pequeño.</li>
 *   <li>La generación se usa primero para cubrir la demanda.</li>
 *   <li>El sobrante carga la batería; si falta, se descarga la batería.</li>
 *   <li>Si ni así se cubre la demanda, la diferencia queda sin servir ({@code unservedKw}).</li>
 * </ol>
 * Decidir qué consumidores se quedan sin energía (recorte de cargas) es responsabilidad de la
 * tarea 3; aquí solo se calcula cuánta energía hay.
 */
public final class PowerSystem {

    /** Amplitud del ruido de generación: ±2 % (especificación, apartado 3.3). */
    static final double GENERATION_NOISE = 0.02;

    private final Orbit orbit;
    private final List<SolarPanel> panels;
    private final Battery battery;

    public PowerSystem(Orbit orbit, List<SolarPanel> panels, Battery battery) {
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("Hace falta al menos un panel");
        }
        this.orbit = orbit;
        // List.copyOf conserva el orden: los paneles se recorren siempre igual (determinismo).
        this.panels = List.copyOf(panels);
        this.battery = battery;
    }

    /** Configuración de referencia de NEXUS-9: dos paneles de 6 kW y batería de 20 kWh al 80 %. */
    public static PowerSystem standard() {
        return new PowerSystem(
                Orbit.LOW_EARTH_ORBIT,
                List.of(new SolarPanel("SOLAR_PANEL_A", 6.0), new SolarPanel("SOLAR_PANEL_B", 6.0)),
                new Battery(20.0, 16.0));
    }

    public PowerReading step(long tick, double demandKw, RandomSource random) {
        if (demandKw < 0) {
            throw new IllegalArgumentException("demandKw no puede ser negativa: " + demandKw);
        }
        boolean sunlit = orbit.isSunlit(tick);

        double generationKw = 0.0;
        for (SolarPanel panel : panels) {
            double output = panel.outputKw(sunlit);
            // Solo se consume aleatoriedad si el panel produce: así la sombra no altera la secuencia
            // de ruido de forma dependiente de la eficiencia. El orden de llamadas es fijo.
            if (output > 0) {
                output *= random.noiseFactor(GENERATION_NOISE);
            }
            generationKw += output;
        }

        double h = SimulationTime.HOURS_PER_TICK;
        double servedKw;
        if (generationKw >= demandKw) {
            servedKw = demandKw;
            battery.charge((generationKw - demandKw) * h);
        } else {
            double missingKwh = (demandKw - generationKw) * h;
            double fromBatteryKwh = battery.discharge(missingKwh);
            servedKw = generationKw + fromBatteryKwh / h;
        }

        return new PowerReading(
                tick,
                sunlit,
                generationKw,
                demandKw,
                servedKw,
                battery.chargeKwh(),
                battery.chargePercent());
    }

    public SolarPanel panel(String id) {
        return panels.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No existe el panel " + id));
    }

    public List<SolarPanel> panels() {
        return panels;
    }

    public Battery battery() {
        return battery;
    }

    /** Potencia máxima que generan todos los paneles sanos con luz. */
    public double nominalGenerationKw() {
        return panels.stream().mapToDouble(SolarPanel::nominalKw).sum();
    }
}
