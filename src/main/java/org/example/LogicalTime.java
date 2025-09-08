package org.example;

/**
 * Simple total-ordbare logische Zeit:
 *  - obere 16 Bit: Rank (0..65535)
 *  - untere 48 Bit: lokaler Zähler (seq)
 * Ausgabeformat r:s (rank:seq) zur Lesbarkeit.
 */
public final class LogicalTime {
    private final int rank;
    private long seq = 0L;

    public LogicalTime(int rank) {
        if (rank < 0 || rank > 0xFFFF) throw new IllegalArgumentException("rank out of range");
        this.rank = rank;
    }

    /** Erhöht lokalen Zähler und gibt die zusammengesetzte logische Zeit zurück. */
    public long tick() {
        seq++;
        return compose(rank, seq);
    }

    public static long compose(int rank, long seq) {
        return ((long)(rank & 0xFFFF) << 48) | (seq & 0x0000FFFFFFFFFFFFL);
    }
    public static int  rankOf(long ts) { return (int)((ts >>> 48) & 0xFFFF); }
    public static long seqOf (long ts) { return ts & 0x0000FFFFFFFFFFFFL; }

    public static String fmt(long ts) { return rankOf(ts) + ":" + seqOf(ts); }
}
