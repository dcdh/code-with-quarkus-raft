package org.acme;

import org.apache.commons.lang3.Validate;

import java.util.Objects;

public record Quorum(Integer required) {

    public Quorum {
        Objects.requireNonNull(required);
        Validate.isTrue(required > 0, "Quorum required must be greater than 0");
    }
}
