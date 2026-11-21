package com.nexus9.backend.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** On a public demo, deletes simulations older than {@code nexus.retention}. Disabled unless set. */
@Component
@ConditionalOnProperty("nexus.retention")
public class SimulationRetention {

    private static final Logger log = LoggerFactory.getLogger(SimulationRetention.class);

    private final SimulationRepository simulations;
    private final StationRegistry stations;
    private final TransactionTemplate tx;
    private final Duration retention;

    public SimulationRetention(SimulationRepository simulations, StationRegistry stations, TransactionTemplate tx,
            @Value("${nexus.retention}") Duration retention) {
        this.simulations = simulations;
        this.stations = stations;
        this.tx = tx;
        this.retention = retention;
    }

    @Scheduled(initialDelayString = "1m", fixedDelayString = "1h")
    public void purge() {
        purgeOlderThan(Instant.now().minus(retention));
    }

    List<UUID> purgeOlderThan(Instant cutoff) {
        List<UUID> deleted = tx.execute(status -> simulations.deleteCreatedBefore(cutoff));
        deleted.forEach(stations::evict);
        if (!deleted.isEmpty()) {
            log.info("Deleted {} simulations created before {}", deleted.size(), cutoff);
        }
        return deleted;
    }
}
