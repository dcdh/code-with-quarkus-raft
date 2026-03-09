package org.acme;

import io.quarkus.logging.Log;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.acme.event.ElectionTick;
import org.acme.event.HeartbeatTick;
import org.acme.event.RoleChanged;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Singleton
public class RaftService implements NodeResponseHandler {

    public static final String TERM = "term";
    public static final String VOTE = "vote";

    @Inject
    RaftConfig config;

    @Inject
    Client client;

    @Inject
    QuorumProvider quorumProvider;

    @Inject
    Event<RoleChanged> roleChangedEvent;

    @Inject
    ElectionTimer electionTimer;

    RaftState state = new RaftState();

    private void startElection(@Observes final ElectionTick tick) {
        if (state.role == Role.LEADER) {
            return;
        } else {
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
            electionTimer.restartElectionTimer();
        }
    }

    private void becomeLeader() {
        if (state.role == Role.LEADER)
            return;
        changeRole(Role.LEADER);
    }

    void sendHeartbeat(@Observes final HeartbeatTick tick) {
        if (state.role != Role.LEADER) {
            return;
        } else {
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
                        electionTimer.restartElectionTimer();
                    }
                }
            });
        }
    }

    @Override
    public void on(final HeartbeatResponse heartbeatResponse) {
        Objects.requireNonNull(heartbeatResponse);
        if (heartbeatResponse.term().isGreaterThanOrEqualTo(state.term)) {
            state.term = heartbeatResponse.term();
            changeRole(Role.FOLLOWER);
            electionTimer.restartElectionTimer();
        }
    }

    @Override
    public boolean on(final VoteResponse voteResponse) {
        Objects.requireNonNull(voteResponse);
        final Term term = voteResponse.term();
        if (term.isGreaterThan(state.term)) {
            state.term = term;
            changeRole(Role.FOLLOWER);
            electionTimer.restartElectionTimer();
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
