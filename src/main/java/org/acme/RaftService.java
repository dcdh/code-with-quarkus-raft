package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

@QuarkusMain
public class RaftService implements QuarkusApplication {

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    RaftState state = new RaftState();

    HttpClient client;

    Random random = new Random();

    long electionTimer;

    long heartbeatTimer;

    @Override
    public int run(String... args) throws Exception {
        client = vertx.createHttpClient();
        startServer();
        resetElectionTimer();
        heartbeatTimer = vertx.setPeriodic(
                config.heartbeatInterval(),
                id -> {
                    try {
                        sendHeartbeat();
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                });

        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
        return 0;
    }

    void startServer() {
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

    void resetElectionTimer() {
        vertx.cancelTimer(electionTimer);
        long timeout =
                config.electionTimeoutMin() +
                        random.nextInt(
                                config.electionTimeoutMax()
                                        - config.electionTimeoutMin());
        electionTimer = vertx.setTimer(
                timeout,
                id -> {
                    try {
                        startElection();
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    void startElection() throws URISyntaxException {
        if (state.role == Role.LEADER)
            return;
        state.role = Role.CANDIDATE;
        state.term++;
        state.votedFor = config.nodeId();
        int[] votes = {1};
        for (String peer : config.peers()) {
            final URI uri = new URI(peer);
            if (uri.getPort() == config.port()) {
                continue;
            }
            JsonObject body = new JsonObject()
                    .put("term", state.term)
                    .put("candidate", config.nodeId());

            client.request(HttpMethod.POST, uri.getPort(), uri.getHost(), "/raft/vote")
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

    void becomeLeader() {
        if (state.role == Role.LEADER)
            return;
        state.role = Role.LEADER;
        onLeader();
    }

    void sendHeartbeat() throws URISyntaxException {
        if (state.role != Role.LEADER)
            return;

        JsonObject body = new JsonObject().put("term", state.term);
        int[] successfulResponses = {1}; // on compte soi-même
        int quorum = config.peers().size() / 2 + 1;

        for (String peer : config.peers()) {
            final URI uri = new URI(peer);
            if (uri.getPort() == config.port()) {
                continue;
            }

            client.request(HttpMethod.POST, uri.getPort(), uri.getHost(), "/raft/heartbeat")
                    .compose(req -> req.send(body.encode()))
                    .onSuccess(resp -> {
                        successfulResponses[0]++;
                    })
                    .onFailure(err -> Log.debug("Erreur heartbeat vers " + peer + ": " + err));
        }

        // Vérification après un petit délai pour permettre aux réponses d’arriver
        vertx.setTimer(config.heartbeatInterval() / 2, id -> {
            if (successfulResponses[0] < quorum) {
                Log.warn("Leader lost quorum, stepping down → " + config.nodeId());
                if (state.role == Role.LEADER) {
                    onLostLeadership();
                    state.role = Role.FOLLOWER;
                    resetElectionTimer();
                }
            }
        });
    }

    void receiveHeartbeat(int term) {
        if (term >= state.term) {
            if (state.role == Role.LEADER) {
                onLostLeadership();
            }
            state.role = Role.FOLLOWER;
            state.term = term;
            state.lastHeartbeat = System.currentTimeMillis();
            resetElectionTimer();
        }
    }

    boolean requestVote(int term, String candidate) {
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

    void onLeader() {
        Log.info("LEADER ELECTED → " + config.nodeId());
        // start Debezium
    }

    void onLostLeadership() {
        Log.info("LEADER LOST → " + config.nodeId());
        // stop Debezium
    }
}
