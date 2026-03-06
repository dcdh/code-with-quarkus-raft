package org.acme;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.net.URI;
import java.util.List;

@ConfigMapping(prefix = "raft")
public interface RaftConfig {

    String nodeId();

    int port();

    List<URI> peers();

    @WithDefault("800")
    int electionTimeoutMin();

    @WithDefault("1600")
    int electionTimeoutMax();

    @WithDefault("200")
    int heartbeatInterval();
}
