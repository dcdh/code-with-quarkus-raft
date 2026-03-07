package org.acme;

import java.util.Objects;

public record VoteResponse(Term term) {

    public VoteResponse {
        Objects.requireNonNull(term);
    }
}
