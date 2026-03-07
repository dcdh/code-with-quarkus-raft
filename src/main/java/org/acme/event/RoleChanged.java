package org.acme.event;

import org.acme.Role;
import org.acme.Term;

import java.util.Objects;

public record RoleChanged(Role previousRole, Role newRole, Term term) {

    public RoleChanged {
        Objects.requireNonNull(previousRole);
        Objects.requireNonNull(newRole);
        Objects.requireNonNull(term);
    }
}
