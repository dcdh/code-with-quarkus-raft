package org.acme;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DefaultQuorumProvider implements QuorumProvider {

    @Inject
    RaftConfig config;

    private Quorum quorum;

    @Override
    public Quorum provide() {
        return quorum;
    }

    public void onStart(@Observes StartupEvent startup) {
        quorum = new Quorum(config.peers().size() / 2 + 1);
    }
}

