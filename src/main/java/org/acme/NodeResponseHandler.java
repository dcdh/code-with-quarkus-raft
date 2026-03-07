package org.acme;

public interface NodeResponseHandler {

    void on(HeartbeatResponse heartbeatResponse);

    boolean on(VoteResponse voteResponse);
}
