package org.acme;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public interface Client {

    void vote(URI peer, Term term, Consumer<VoteGranted> onVoteGranted);

    void sendHeartbeats(List<URI> peers, Term term, Consumer<SuccessfulResponses> onSuccessfulResponses);
}
