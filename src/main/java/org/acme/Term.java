package org.acme;

import org.apache.commons.lang3.Validate;

import java.util.Objects;

public final class Term {

    private Integer current;

    public Term(final Integer current) {
        Objects.requireNonNull(current);
        Validate.isTrue(current >= 0, "Term must be greater than or equal to 0");
        this.current = current;
    }

    public Term() {
        current = 0;
    }

    public void increment() {
        current++;
    }

    public Integer current() {
        return current;
    }

    public boolean isGreaterThanOrEqualTo(final Term fromOtherNode) {
        return this.current >= fromOtherNode.current();
    }

    public boolean isGreaterThan(final Term fromOtherNode) {
        return this.current > fromOtherNode.current();
    }
}
