package org.acme;

import org.apache.commons.lang3.Validate;

import java.util.Objects;

public record VoteResponse(Integer term, String candidate) {

    public VoteResponse {
        Objects.requireNonNull(term);
        Objects.requireNonNull(candidate);
        Validate.isTrue(term > 0, "Term must be greater than 0");
    }
}
