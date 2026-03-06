package org.acme;

public interface ServerResponseHandler {

    void on(HeartbeatResponse heartbeatResponse);

    boolean on(VoteResponse voteResponse);
}
