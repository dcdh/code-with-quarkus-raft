package org.acme.api;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.acme.*;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Singleton
public class VertxClient implements Client {

    HttpClient client;

    @Inject
    Vertx vertx;

    public void onStart(@Observes StartupEvent startup) {
        client = vertx.createHttpClient();
    }

    @Override
    public void vote(final URI peer, final Term term, final Consumer<VoteGranted> onVoteGranted) {
        Objects.requireNonNull(peer);
        Objects.requireNonNull(term);
        Objects.requireNonNull(onVoteGranted);
        JsonObject body = new JsonObject()
                .put(RaftService.TERM, term.current());
        client.request(HttpMethod.POST, peer.getPort(), peer.getHost(), "/raft/vote")
                .compose(req -> req.send(body.encode()))
                .onSuccess(response ->
                        response.body().onSuccess(buffer -> {
                                    JsonObject json = buffer.toJsonObject();
                                    onVoteGranted.accept(new VoteGranted(json.getBoolean(RaftService.VOTE)));
                                })
                                .onFailure(err -> Log.debug("Erreur lecture body: " + err))
                )
                .onFailure(err -> Log.debug("Erreur requête vers " + peer + ": " + err));

    }

    @Override
    public void sendHeartbeats(final List<URI> peers, final Term term,
                               final Consumer<SuccessfulResponses> onSuccessfulResponses) {
        Objects.requireNonNull(peers);
        Objects.requireNonNull(term);
        Objects.requireNonNull(onSuccessfulResponses);
        final JsonObject body = new JsonObject().put(RaftService.TERM, term.current());
        final SuccessfulResponses successfulResponses = new SuccessfulResponses();
        final List<Future<HttpClientResponse>> heartbeats = peers
                .stream()
                .map(peer -> client.request(HttpMethod.POST, peer.getPort(), peer.getHost(), "/raft/heartbeat")
                        .compose(req -> req.send(body.encode()))
                        .onSuccess(resp -> successfulResponses.increment())
                        .onFailure(err -> Log.debug("Erreur heartbeat vers " + peer + ": " + err)))
                .toList();
        Future.join(heartbeats).onComplete(_ -> onSuccessfulResponses.accept(successfulResponses));
    }
}
