package org.acme.server;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
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
    ServerResponseHandler serverResponseHandler;

    void init(@Observes StartupEvent ev) {
        vertx.createHttpServer()
                .requestHandler(req -> {
                    if (req.path().equals("/raft/heartbeat")) {
                        req.bodyHandler(buffer -> {
                            JsonObject json = buffer.toJsonObject();
                            serverResponseHandler.on(new HeartbeatResponse(json.getInteger(RaftService.TERM)));
                            req.response().end();
                        });
                    } else if (req.path().equals("/raft/vote")) {
                        req.bodyHandler(buffer -> {
                            JsonObject json = buffer.toJsonObject();
                            boolean vote = serverResponseHandler.on(new VoteResponse(json.getInteger(RaftService.TERM)));
                            req.response()
                                    .end(new JsonObject()
                                            .put(RaftService.VOTE, vote)
                                            .encode());
                        });
                    }
                })
                .listen(config.port());
    }
}
