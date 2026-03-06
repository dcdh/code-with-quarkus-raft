package org.acme;

import java.util.Objects;

public record VoteGranted(Boolean vote) {

    public VoteGranted {
        Objects.requireNonNull(vote);
    }
}
