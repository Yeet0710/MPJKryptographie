package org.example;

import java.io.PrintStream;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    public enum Level { INFO } // immer aktiv

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static volatile int rank = -1;
    private static volatile int size = -1;
    private static volatile String host = "?";

    private Log() {}

    /** Nach MPI.Init(...) aufrufen. */
    public static void init(int mpiRank, int mpiSize) {
        rank = mpiRank;
        size = mpiSize;
        try { host = InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) {}
    }

    /* ===== Convenience-API: Start → Step → End ===== */
    public static void processStart(String activity, Object... args) {
        print(System.out, "START ", activity, args, null);
    }
    public static void processStep(String activity, Object... args) {
        print(System.out, "STEP  ", activity, args, null);
    }
    public static void processEnd(String activity, Object... args) {
        print(System.out, "END   ", activity, args, null);
    }
    public static void info(String msg, Object... args) {
        print(System.out, "INFO  ", msg, args, null);
    }
    public static void warn(String msg, Object... args) {
        print(System.err, "WARN  ", msg, args, null);
    }
    public static void error(String msg, Throwable t, Object... args) {
        print(System.err, "ERROR ", msg, args, t);
    }

    private static void print(PrintStream out, String tag, String msg, Object[] args, Throwable t) {
        String ts = LocalDateTime.now().format(TS);
        String th = Thread.currentThread().getName();
        String prefix = String.format("%s [r%d/%d@%s] [%s] %s", ts, rank, size, host, th, tag);
        String text = (args == null || args.length == 0) ? msg : String.format(msg, args);

        synchronized (LOCK) {
            out.print(prefix);
            out.print(text);
            out.println();
            if (t != null) t.printStackTrace(out);
            out.flush();
        }
    }
}
