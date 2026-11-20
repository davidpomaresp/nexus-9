package com.nexus9.simulator.power;

/**
 * Lectura de telemetría del sistema de energía en un tick.
 *
 * @param tick            tick al que corresponde la lectura
 * @param sunlit          si la estación está en la fase de luz de la órbita
 * @param generationKw    potencia generada por los paneles (con ruido)
 * @param demandKw        potencia pedida por los consumidores
 * @param servedKw        potencia realmente entregada; menor que la demanda si falta energía
 * @param batteryKwh      carga de la batería al final del tick
 * @param batteryPercent  carga de la batería al final del tick, en %
 */
public record PowerReading(
        long tick,
        boolean sunlit,
        double generationKw,
        double demandKw,
        double servedKw,
        double batteryKwh,
        double batteryPercent) {

    /** Potencia que no se ha podido servir. 0 si la demanda está cubierta. */
    public double unservedKw() {
        return demandKw - servedKw;
    }
}
