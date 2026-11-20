package com.nexus9.simulator.power;

import com.nexus9.simulator.Orbit;
import com.nexus9.simulator.RandomSource;
import com.nexus9.simulator.SimulationTime;
import java.util.List;
import java.util.NoSuchElementException;

public final class PowerSystem {

    static final double GENERATION_NOISE = 0.02;

    private final Orbit orbit;
    private final List<SolarPanel> panels;
    private final Battery battery;

    public PowerSystem(Orbit orbit, List<SolarPanel> panels, Battery battery) {
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("at least one panel is required");
        }
        this.orbit = orbit;
        this.panels = List.copyOf(panels);
        this.battery = battery;
    }

    public static PowerSystem standard() {
        return new PowerSystem(
                Orbit.LOW_EARTH_ORBIT,
                List.of(new SolarPanel("SOLAR_PANEL_A", 6.0), new SolarPanel("SOLAR_PANEL_B", 6.0)),
                new Battery(20.0, 16.0));
    }

    public PowerReading step(long tick, double demandKw, RandomSource random) {
        return settle(tick, generate(tick, random), demandKw);
    }

    public double generate(long tick, RandomSource random) {
        boolean sunlit = orbit.isSunlit(tick);
        double generationKw = 0.0;
        for (SolarPanel panel : panels) {
            double output = panel.outputKw(sunlit);
            if (output > 0) {
                output *= random.noiseFactor(GENERATION_NOISE);
            }
            generationKw += output;
        }
        return generationKw;
    }

    /** Maximum power that can be delivered this tick: generation plus whatever the battery holds. */
    public double availableKw(double generationKw) {
        return generationKw + battery.chargeKwh() / SimulationTime.HOURS_PER_TICK;
    }

    public PowerReading settle(long tick, double generationKw, double demandKw) {
        if (demandKw < 0) {
            throw new IllegalArgumentException("demandKw must not be negative");
        }
        double hours = SimulationTime.HOURS_PER_TICK;
        double servedKw;
        if (generationKw >= demandKw) {
            servedKw = demandKw;
            battery.charge((generationKw - demandKw) * hours);
        } else {
            double fromBatteryKwh = battery.discharge((demandKw - generationKw) * hours);
            servedKw = generationKw + fromBatteryKwh / hours;
        }
        return new PowerReading(tick, orbit.isSunlit(tick), generationKw, demandKw, servedKw,
                battery.chargeKwh(), battery.chargePercent());
    }

    public SolarPanel panel(String id) {
        return panels.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("unknown panel: " + id));
    }

    public List<SolarPanel> panels() {
        return panels;
    }

    public Battery battery() {
        return battery;
    }

    public double nominalGenerationKw() {
        return panels.stream().mapToDouble(SolarPanel::nominalKw).sum();
    }
}
