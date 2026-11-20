package com.nexus9.backend.api;

import com.nexus9.backend.simulation.SimulationAdvanced;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events: each open panel gets a "state" event every time its simulation changes.
 * Browsers reconnect on their own when the stream times out.
 */
@Component
class SimulationEvents {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    SseEmitter subscribe(SimulationResponse current) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(current.id(), id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, current);
        return emitter;
    }

    @EventListener
    void onAdvanced(SimulationAdvanced event) {
        SimulationResponse state =
                SimulationResponse.of(event.progress().simulation(), event.progress().openIncidents());
        for (SseEmitter emitter : emitters.getOrDefault(state.id(), List.of())) {
            send(emitter, state);
        }
    }

    int subscribers(UUID id) {
        return emitters.getOrDefault(id, List.of()).size();
    }

    private void send(SseEmitter emitter, SimulationResponse state) {
        try {
            emitter.send(SseEmitter.event().name("state").data(state));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
