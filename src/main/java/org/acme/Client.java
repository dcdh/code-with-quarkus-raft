package org.acme;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public interface Client {

    void vote(URI peer, Integer term, Consumer<VoteGranted> onVoteGranted);

    void sendHeartbeats(List<URI> peers, Integer term, Consumer<SuccessfulResponses> onSuccessfulResponses);
}
