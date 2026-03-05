package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.concurrent.CountDownLatch;

@QuarkusMain
public class ApplicationEntrypoint implements QuarkusApplication {

    @Inject
    RaftConfig config;

    @Override
    public int run(String... args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
        return 0;
    }

    void onLeader(@Observes OnLeaderElected onLeaderElected) {
        Log.info("LEADER ELECTED → " + config.nodeId());
        // start Debezium
    }

    void onLostLeadership(@Observes OnLostLeadership onLostLeadership) {
        Log.info("LEADER LOST → " + config.nodeId());
        // stop Debezium
    }
}
