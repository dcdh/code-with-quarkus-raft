package org.acme;

import java.util.Objects;

public record HeartbeatResponse(Term term) {

    public HeartbeatResponse {
        Objects.requireNonNull(term);
    }
}
