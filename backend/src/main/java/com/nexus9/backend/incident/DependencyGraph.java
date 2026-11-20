package com.nexus9.backend.incident;

import com.nexus9.backend.simulation.StationModule;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Which modules depend on which. Used to tell what else an incident may affect. */
public final class DependencyGraph {

    public static final DependencyGraph STATION = new DependencyGraph(Map.of(
            StationModule.OXYGEN, List.of(StationModule.POWER),
            StationModule.COMMS, List.of(StationModule.POWER)));

    private final Map<StationModule, List<StationModule>> dependents = new EnumMap<>(StationModule.class);

    public DependencyGraph(Map<StationModule, List<StationModule>> dependsOn) {
        dependsOn.forEach((module, dependencies) -> dependencies.forEach(dependency ->
                dependents.computeIfAbsent(dependency, k -> new ArrayList<>()).add(module)));
        dependents.values().forEach(list -> list.sort(null));
    }

    /** Modules that directly or indirectly depend on {@code module} (breadth-first). */
    public List<StationModule> affectedBy(StationModule module) {
        Set<StationModule> visited = EnumSet.of(module);
        List<StationModule> result = new ArrayList<>();
        Queue<StationModule> queue = new ArrayDeque<>(List.of(module));
        while (!queue.isEmpty()) {
            for (StationModule next : dependents.getOrDefault(queue.poll(), List.of())) {
                if (visited.add(next)) {
                    result.add(next);
                    queue.add(next);
                }
            }
        }
        return result;
    }
}
