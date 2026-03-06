package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;
import java.util.Random;

@Singleton
public class RaftService {

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    @Inject
    Event<OnLeaderElected> onLeaderElectedEvent;

    @Inject
    Event<OnLostLeadership> onLostLeadershipEvent;

    RaftState state = new RaftState();

    HttpClient client;

    Random random = new Random();

    long electionTimer;

    public void onStart(@Observes StartupEvent startup) {
        client = vertx.createHttpClient();
        startServer();
        resetElectionTimer();
        vertx.setPeriodic(
                config.heartbeatInterval(),
                id -> sendHeartbeat());
    }

    private void startServer() {
        vertx.createHttpServer()
                .requestHandler(req -> {
                    if (req.path().equals("/raft/heartbeat")) {
                        req.bodyHandler(buffer -> {
                            JsonObject json = buffer.toJsonObject();
                            receiveHeartbeat(json.getInteger("term"));
                            req.response().end();
                        });
                    } else if (req.path().equals("/raft/vote")) {
                        req.bodyHandler(buffer -> {
                            JsonObject json = buffer.toJsonObject();
                            boolean vote =
                                    requestVote(
                                            json.getInteger("term"),
                                            json.getString("candidate"));
                            req.response()
                                    .end(new JsonObject()
                                            .put("vote", vote)
                                            .encode());
                        });
                    }
                })
                .listen(config.port());
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
            JsonObject body = new JsonObject()
                    .put("term", state.term)
                    .put("candidate", config.nodeId());

            client.request(HttpMethod.POST, peer.getPort(), peer.getHost(), "/raft/vote")
                    .compose(req -> req.send(body.encode()))
                    .onSuccess(response -> {
                        response.body().onSuccess(buffer -> {
                                    JsonObject json = buffer.toJsonObject();
                                    boolean voteGranted = json.getBoolean("vote");
                                    if (voteGranted) {
                                        votes[0]++;
                                        if (votes[0] > config.peers().size() / 2) {
                                            becomeLeader();
                                        }
                                    }
                                })
                                .onFailure(err -> Log.debug("Erreur lecture body: " + err));
                    })
                    .onFailure(err -> Log.debug("Erreur requête vers " + peer + ": " + err));
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

        JsonObject body = new JsonObject().put("term", state.term);
        int[] successfulResponses = {1}; // on compte soi-même
        int quorum = config.peers().size() / 2 + 1;

        final List<Future<HttpClientResponse>> heartbeats = config.peers()
                .stream()
                .filter(peer -> peer.getPort() != config.port())
                .map(peer -> client.request(HttpMethod.POST, peer.getPort(), peer.getHost(), "/raft/heartbeat")
                        .compose(req -> req.send(body.encode()))
                        .onSuccess(resp -> {
                            successfulResponses[0]++;
                        })
                        .onFailure(err -> Log.debug("Erreur heartbeat vers " + peer + ": " + err)))
                .toList();
        Future.join(heartbeats).onComplete(result -> {
            if (successfulResponses[0] < quorum) {
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
}
