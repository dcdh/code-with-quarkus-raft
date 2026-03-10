package org.acme.server;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.acme.*;

@Singleton
public class VertxServer {

    @Inject
    Vertx vertx;

    @Inject
    RaftConfig config;

    @Inject
    NodeResponseHandler nodeResponseHandler;

    private HttpServer httpServer;

    void on(@Observes final StartupEvent ev) {
        httpServer = vertx.createHttpServer()
                .requestHandler(req -> {
                    if (req.path().equals("/raft/heartbeat")) {
                        req.bodyHandler(buffer -> {
                            JsonObject json = buffer.toJsonObject();
                            nodeResponseHandler.on(new HeartbeatResponse(
                                    new Term(json.getInteger(RaftService.TERM))));
                            req.response().end();
                        });
                    } else if (req.path().equals("/raft/vote")) {
                        req.bodyHandler(buffer -> {
                            JsonObject json = buffer.toJsonObject();
                            boolean vote = nodeResponseHandler.on(new VoteResponse(
                                    new Term(json.getInteger(RaftService.TERM))));
                            req.response()
                                    .end(new JsonObject()
                                            .put(RaftService.VOTE, vote)
                                            .encode());
                        });
                    }
                })
                .listen(config.port(), httpServerAsyncResult -> {
                    if (httpServerAsyncResult.succeeded()) {
                        Log.infov("Server started successfully on port {0}", httpServerAsyncResult.result().actualPort());
                    } else {
                        throw new RuntimeException("Server failed to start", httpServerAsyncResult.cause());
                    }
                });
    }

    void on(@Observes final ShutdownEvent shutdownEvent) {
        httpServer.close().toCompletionStage().toCompletableFuture().join();
    }
}
