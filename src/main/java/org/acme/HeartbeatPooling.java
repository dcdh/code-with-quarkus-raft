package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.acme.event.HeartbeatTick;

@Singleton
public class HeartbeatPooling {

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    @Inject
    Event<HeartbeatTick> heartbeatTickEvent;

    private Long heartbeatTimer;

    void on(@Observes final StartupEvent startupEvent) {
        Log.info("Heartbeat pooling started");
        heartbeatTimer = vertx.setPeriodic(
                config.heartbeatInterval(),
                id -> heartbeatTickEvent.fire(new HeartbeatTick()));
    }

    void on(@Observes final ShutdownEvent shutdownEvent) {
        vertx.cancelTimer(heartbeatTimer);
    }

    void on(@Observes final HeartbeatTick tick) {
        Log.trace("Heartbeat pooling tick");
    }
}
