package org.example;

import java.io.Serial;
import java.io.Serializable;

public class ProcessRun implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public final int rank;
    public final String host;
    public final long startMs;
    public final long endMs;
    public final long durationMs;
    public final boolean foundPrime;

    public ProcessRun(int rank, String host, long startMs, long endMs, boolean foundPrime) {
        this.rank = rank;
        this.host = host;
        this.startMs = startMs;
        this.endMs = endMs;
        this.durationMs = Math.max(0, endMs - startMs);
        this.foundPrime = foundPrime;
    }

    @Override
    public String toString() {
        return "Rank " + rank + " @ " + host +
                " | duration=" + durationMs + "ms" +
                " | foundPrime=" + foundPrime;
    }
}
