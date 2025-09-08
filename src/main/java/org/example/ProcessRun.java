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

    // Logische Zeiten
    public final long ltsStart;     // erster Tick des Prozesses
    public final long ltsFound;     // Tick beim Fund (oder -1)
    public final long ltsEnd;       // letzter Tick des Prozesses

    // Bitlängen
    public final int bitsRequested; // gewünschte Bitlänge (Parameter)
    public final int bitsActual;    // tatsächliche Bitlänge des Fundes oder -1

    public ProcessRun(int rank, String host, long startMs, long endMs, boolean foundPrime,
                      long ltsStart, long ltsFound, long ltsEnd,
                      int bitsRequested, int bitsActual) {
        this.rank = rank;
        this.host = host;
        this.startMs = startMs;
        this.endMs = endMs;
        this.durationMs = Math.max(0, endMs - startMs);
        this.foundPrime = foundPrime;

        this.ltsStart = ltsStart;
        this.ltsFound = ltsFound;
        this.ltsEnd = ltsEnd;

        this.bitsRequested = bitsRequested;
        this.bitsActual = bitsActual;
    }

    @Override
    public String toString() {
        return "Rank " + rank + " @ " + host +
                " | duration=" + durationMs + "ms" +
                " | foundPrime=" + foundPrime +
                " | bits=" + (bitsActual >= 0 ? (bitsActual + "/" + bitsRequested) : ("-/" + bitsRequested)) +
                " | LTS start=" + LogicalTime.fmt(ltsStart) +
                (ltsFound >= 0 ? (" found=" + LogicalTime.fmt(ltsFound)) : "") +
                " end=" + LogicalTime.fmt(ltsEnd);
    }
}
