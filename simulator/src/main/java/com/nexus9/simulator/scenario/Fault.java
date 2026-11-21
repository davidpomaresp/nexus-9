package com.nexus9.simulator.scenario;

/** Something that happens to the station at a given tick. */
public sealed interface Fault permits PanelFault, OxygenLeak {

    long tick();
}
