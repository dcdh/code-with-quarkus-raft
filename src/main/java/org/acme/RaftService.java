package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.acme.event.RoleChanged;

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
    Event<RoleChanged> roleChangedEvent;

    RaftState state = new RaftState();

    Random random = new Random();

    Long electionTimer;

    public void onStart(@Observes StartupEvent startup) {
        vertx.setPeriodic(
                config.heartbeatInterval(),
                id -> sendHeartbeat());
        restartElectionTimer();
    }

    private void restartElectionTimer() {
        // vertx.cancelTimer(0) will cancel all event the one defined using setPeriodic
        if (electionTimer != null) {
            vertx.cancelTimer(electionTimer);
        }
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
        changeRole(Role.CANDIDATE);
        state.term.increment();
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
        changeRole(Role.LEADER);
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
                    changeRole(Role.FOLLOWER);
                    restartElectionTimer();
                }
            }
        });
    }

    @Override
    public void on(final HeartbeatResponse heartbeatResponse) {
        Objects.requireNonNull(heartbeatResponse);
        if (heartbeatResponse.term().isGreaterThanOrEqualTo(state.term)) {
            state.term = heartbeatResponse.term();
            changeRole(Role.FOLLOWER);
            restartElectionTimer();
        }
    }

    @Override
    public boolean on(final VoteResponse voteResponse) {
        Objects.requireNonNull(voteResponse);
        final Term term = voteResponse.term();
        if (term.isGreaterThan(state.term)) {
            state.term = term;
            changeRole(Role.FOLLOWER);
            restartElectionTimer();
            return true;
        }
        return false;
    }

    private void changeRole(final Role newRole) {
        Objects.requireNonNull(newRole);
        if (state.role == newRole) {
            return;
        }

        final Role previous = state.role;
        state.role = newRole;

        roleChangedEvent.fire(new RoleChanged(previous, newRole, state.term));
    }
}
