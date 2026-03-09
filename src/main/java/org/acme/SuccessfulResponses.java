package org.acme;

public class SuccessfulResponses {

    // on compte soi-même
    private int count = 1;

    public synchronized void increment() {
        count++;
    }

    public boolean isBellowQuorum(final Quorum quorum) {
        return count < quorum.required();
    }

    @Override
    public String toString() {
        return "SuccessfulResponses{" +
                "count=" + count +
                '}';
    }
}
