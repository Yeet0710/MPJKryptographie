package org.example;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Datei-Logger für MPI-Jobs: schreibt NUR in eine Logdatei (keine Konsole).
 * Zeilenformat: Zeitstempel, Rank/Size, Host, Thread, Tag + Nachricht.
 * Pro Prozess (Rank) wird eine eigene Datei unter logs/ erzeugt.
 *
 * Konfiguration (optional):
 *  - System-Property log.dir = Zielordner (Default: "logs")
 *  - Dateiname: mpj-YYYYMMDD-HHMMSS-r<R>-of-<Size>-<Host>.log
 */
public final class Log {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static volatile int rank = -1;
    private static volatile int size = -1;
    private static volatile String host = "?";
    private static volatile PrintWriter writer = null;
    private static volatile Path logFilePath = null;

    private Log() {}

    /** Nach MPI.Init(...) aufrufen. Erstellt Datei und schreibt ab dann ausschließlich dorthin. */
    public static void init(int mpiRank, int mpiSize) {
        rank = mpiRank;
        size = mpiSize;

        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}

        String dir = System.getProperty("log.dir", "logs");
        String tsForFile = LocalDateTime.now().format(TS_FILE);
        String safeHost = host.replaceAll("[^A-Za-z0-9_.-]", "_");
        String fileName = String.format("mpj-%s-r%d-of-%d-%s.log", tsForFile, rank, size, safeHost);

        try {
            Path folder = Paths.get(dir);
            Files.createDirectories(folder);
            logFilePath = folder.resolve(fileName);
            writer = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(logFilePath.toFile(), true),
                                    StandardCharsets.UTF_8
                            )
                    ),
                    true // autoFlush
            );
        } catch (Exception e) {
            throw new RuntimeException("Konnte Logdatei nicht erstellen: " + e.getMessage(), e);
        }

        // Erste Zeile ins Log
        info("Logger gestartet. Datei: %s", logFilePath.toAbsolutePath());
    }

    /** Pfad der aktuellen Logdatei (z. B. für Ausgabe oder Tests). */
    public static String getLogFilePath() {
        return (logFilePath == null) ? "" : logFilePath.toAbsolutePath().toString();
    }

    /* ===== Komfort-API: Start → Step → End ===== */
    public static void processStart(String activity, Object... args) {
        print("START ", activity, args, null);
    }

    public static void processStep(String activity, Object... args) {
        print("STEP  ", activity, args, null);
    }

    public static void processEnd(String activity, Object... args) {
        print("END   ", activity, args, null);
    }

    public static void info(String msg, Object... args) {
        print("INFO  ", msg, args, null);
    }

    public static void warn(String msg, Object... args) {
        print("WARN  ", msg, args, null);
    }

    public static void error(String msg, Throwable t, Object... args) {
        print("ERROR ", msg, args, t);
    }

    private static void print(String tag, String msg, Object[] args, Throwable t) {
        if (writer == null) {
            // Logger nicht initialisiert → im Sinne der Anforderung NICHT in Konsole ausweichen.
            // Hart fehlschlagen, damit das im Cluster auffällt.
            throw new IllegalStateException("Logger nicht initialisiert. Log.init(..) vor Nutzung aufrufen.");
        }

        String ts = LocalDateTime.now().format(TS);
        String thread = Thread.currentThread().getName();
        String prefix = String.format("%s [r%d/%d@%s] [%s] %s",
                ts, rank, size, host, thread, tag);
        String text = (args == null || args.length == 0) ? msg : String.format(msg, args);

        synchronized (LOCK) { // zeilenweise atomar pro Prozess
            writer.print(prefix);
            writer.print(text);
            writer.println();
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                writer.print(sw.toString());
            }
            writer.flush();
        }
    }

    /** Zum Ende des Programms aufrufen, um Handles sauber zu schließen. */
    public static void close() {
        synchronized (LOCK) {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
            }
        }
    }
}
