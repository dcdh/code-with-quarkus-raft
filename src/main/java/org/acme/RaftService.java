package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.acme.event.OnLeaderElected;
import org.acme.event.OnLostLeadership;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Singleton
public class RaftService implements ServerResponseHandler {

    public static final String TERM = "term";
    public static final String VOTE = "vote";

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    @Inject
    Client client;

    @Inject
    QuorumProvider quorumProvider;

    @Inject
    Event<OnLeaderElected> onLeaderElectedEvent;

    @Inject
    Event<OnLostLeadership> onLostLeadershipEvent;

    RaftState state = new RaftState();

    Random random = new Random();

    long electionTimer;

    public void onStart(@Observes StartupEvent startup) {
        restartElectionTimer();
        vertx.setPeriodic(
                config.heartbeatInterval(),
                id -> sendHeartbeat());
    }

    private void restartElectionTimer() {
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
        final Votes votes = new Votes();
        final Quorum quorum = quorumProvider.provide();
        for (URI peer : config.peers()) {
            if (peer.getPort() == config.port()) {
                continue;
            }
            client.vote(peer, state.term, voteGranted -> {
                if (state.role != Role.CANDIDATE)
                    return;
                if (voteGranted.vote()) {
                    votes.increment();
                    if (!votes.isBellowQuorum(quorum)) {
                        becomeLeader();
                    }
                }
            });
        }
        restartElectionTimer();
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
        final Quorum quorum = quorumProvider.provide();
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
                    restartElectionTimer();
                }
            }
        });
    }

    @Override
    public void on(final HeartbeatResponse heartbeatResponse) {
        Objects.requireNonNull(heartbeatResponse);
        if (heartbeatResponse.term() >= state.term) {
            if (state.role == Role.LEADER) {
                onLostLeadershipEvent.fire(new OnLostLeadership());
            }
            state.role = Role.FOLLOWER;
            state.term = heartbeatResponse.term();
            state.lastHeartbeat = System.currentTimeMillis();
            restartElectionTimer();
        }
    }

    @Override
    public boolean on(final VoteResponse voteResponse) {
        Objects.requireNonNull(voteResponse);
        final int term = voteResponse.term();
        if (term > state.term) {
            state.term = term;
            state.role = Role.FOLLOWER;
            restartElectionTimer();
            return true;
        }
        return false;
    }
}
