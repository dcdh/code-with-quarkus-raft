package org.acme;

public class RaftState {

    volatile Role role = Role.FOLLOWER;

    volatile int term = 0;

    volatile long lastHeartbeat = System.currentTimeMillis();
}
