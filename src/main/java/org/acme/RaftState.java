package org.acme;

public class RaftState {

    volatile Role role = Role.FOLLOWER;

    volatile int term = 0;

    volatile String votedFor = null;

    volatile long lastHeartbeat = System.currentTimeMillis();
}
