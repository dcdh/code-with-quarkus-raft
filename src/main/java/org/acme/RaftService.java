package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Singleton
public class RaftService implements ServerResponseHandler {

    public static final String TERM = "term";
    public static final String CANDIDATE = "candidate";
    public static final String VOTE = "vote";

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    @Inject
    Client client;

    @Inject
    Event<OnLeaderElected> onLeaderElectedEvent;

    @Inject
    Event<OnLostLeadership> onLostLeadershipEvent;

    RaftState state = new RaftState();

    Random random = new Random();

    long electionTimer;

    public void onStart(@Observes StartupEvent startup) {
        resetElectionTimer();
        vertx.setPeriodic(
                config.heartbeatInterval(),
                id -> sendHeartbeat());
    }

    private void resetElectionTimer() {
        vertx.cancelTimer(electionTimer);
        long timeout =
                config.electionTimeoutMin() +
                        random.nextInt(
                                config.electionTimeoutMax()
                                        - config.electionTimeoutMin());
        electionTimer = vertx.setTimer(
                timeout,
                id -> startElection());
    }

    private void startElection() {
        if (state.role == Role.LEADER)
            return;
        state.role = Role.CANDIDATE;
        state.term++;
        state.votedFor = config.nodeId();
        int[] votes = {1};
        for (URI peer : config.peers()) {
            if (peer.getPort() == config.port()) {
                continue;
            }
            client.vote(peer, state.term, voteGranted -> {
                if (voteGranted.vote()) {
                    votes[0]++;
                    if (votes[0] > config.peers().size() / 2) {
                        becomeLeader();
                    }
                }
            });
        }
        resetElectionTimer();
    }

    private void becomeLeader() {
        if (state.role == Role.LEADER)
            return;
        state.role = Role.LEADER;
        onLeaderElectedEvent.fire(new OnLeaderElected());
    }

    private void sendHeartbeat() {
        if (state.role != Role.LEADER)
            return;

        int quorum = config.peers().size() / 2 + 1;
        final List<URI> peers = config.peers()
                .stream()
                .filter(peer -> peer.getPort() != config.port())
                .toList();
        client.sendHeartbeats(peers, state.term, successfulResponses -> {
            if (successfulResponses.isBellowQuorum(quorum)) {
                Log.warn("Leader lost quorum, stepping down → " + config.nodeId());
                if (state.role == Role.LEADER) {
                    onLostLeadershipEvent.fire(new OnLostLeadership());
                    state.role = Role.FOLLOWER;
                    resetElectionTimer();
                }
            }
        });
    }

    private void receiveHeartbeat(int term) {
        if (term >= state.term) {
            if (state.role == Role.LEADER) {
                onLostLeadershipEvent.fire(new OnLostLeadership());
            }
            state.role = Role.FOLLOWER;
            state.term = term;
            state.lastHeartbeat = System.currentTimeMillis();
            resetElectionTimer();
        }
    }

    private boolean requestVote(int term, String candidate) {
        if (term > state.term) {
            state.term = term;
            state.votedFor = candidate;
            state.role = Role.FOLLOWER;
            resetElectionTimer();
            return true;
        } else if (term == state.term && (state.votedFor == null || state.votedFor.equals(candidate))) {
            state.votedFor = candidate;
            return true;
        }
        return false;
    }

    @Override
    public void on(final HeartbeatResponse heartbeatResponse) {
        Objects.requireNonNull(heartbeatResponse);
        receiveHeartbeat(heartbeatResponse.term());
    }

    @Override
    public boolean on(final VoteResponse voteResponse) {
        Objects.requireNonNull(voteResponse);
        return requestVote(voteResponse.term(), voteResponse.candidate());
    }
}
