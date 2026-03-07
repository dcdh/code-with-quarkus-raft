package org.acme;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.acme.event.LeaderElected;
import org.acme.event.LostLeadership;
import org.acme.event.RoleChanged;

import java.util.Objects;

@ApplicationScoped
public class RoleChangedListener {

    @Inject
    Event<LeaderElected> onLeaderElectedEvent;

    @Inject
    Event<LostLeadership> onLostLeadershipEvent;

    void onRoleChanged(@Observes final RoleChanged event) {
        Objects.requireNonNull(event);
        Log.infov("Role changed from {0} to {1} (term {2})", event.previousRole(), event.newRole(), event.term().current());

        if (event.newRole() == Role.LEADER) {
            onLeaderElectedEvent.fire(new LeaderElected());
        } else if (event.newRole() == Role.FOLLOWER && event.previousRole() == Role.LEADER) {
            onLostLeadershipEvent.fire(new LostLeadership());
        }
    }
}
