package org.acme;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.acme.event.ElectionTick;

import java.util.Random;

@ApplicationScoped
public class ElectionTimer {

    @Inject
    Event<ElectionTick> electionTickEvent;

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    Random random = new Random();

    Long electionTimer;

    void on(@Observes final StartupEvent startupEvent) {
        restartElectionTimer();
    }

    void on(@Observes final ShutdownEvent shutdownEvent) {
        if (electionTimer != null) {
            vertx.cancelTimer(electionTimer);
        }
    }

    public void restartElectionTimer() {
        // vertx.cancelTimer(0) will cancel all event the one defined using setPeriodic
        if (electionTimer != null) {
            vertx.cancelTimer(electionTimer);
        }
        long timeout =
                config.electionTimeoutMin() +
                        random.nextInt(
                                config.electionTimeoutMax()
                                        - config.electionTimeoutMin());
        electionTimer = vertx.setTimer(
                timeout,
                id -> electionTickEvent.fire(new ElectionTick()));
    }
}
