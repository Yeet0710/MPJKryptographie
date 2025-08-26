package org.example;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Datei-Logger für MPI-Jobs: schreibt NUR in eine Logdatei (keine Konsole).
 * Zeilenformat (menschlich lesbar, auditierbar):
 *   <Zeit> | Prozess r<rank>/<size> | Schritt "<name>" <gestartet|durchgeführt|beendet> | PMI-Rank=<rank> | ClientID=<hostname> | Tag=<TAG>
 *
 * Weitere Meldungen (info/warn/error) folgen demselben Prefix und hängen "Nachricht: ..." an.
 *
 * Konfiguration (optional):
 *  - System-Property log.dir = Zielordner (Default: "logs")
 *  - Dateiname: mpj-YYYYMMDD-HHmmss-r<R>-of-<Size>-<Host>.log
 */
public final class Log {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static volatile int rank = -1;
    private static volatile int size = -1;
    private static volatile String host = "?";
    private static volatile String pid = "?";
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

        try {
            // "pid@host" -> nur pid-Anteil
            String jvmName = ManagementFactory.getRuntimeMXBean().getName();
            pid = jvmName.contains("@") ? jvmName.substring(0, jvmName.indexOf('@')) : jvmName;
        } catch (Exception ignored) {}

        String dir = System.getProperty("log.dir", "logs");
        String tsForFile = OffsetDateTime.now().format(TS_FILE);
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

        // Auftaktzeile (meta)
        info("Logger gestartet. Datei=%s, PID=%s", logFilePath.getFileName(), pid);
    }

    /** Pfad der aktuellen Logdatei (z. B. für Tests). */
    public static String getLogFilePath() {
        return (logFilePath == null) ? "" : logFilePath.toAbsolutePath().toString();
    }

    /* ===== Komfort-API für gewünschte Sätze ===== */

    /** "… Schritt "<name>" gestartet …" */
    public static void processStart(String stepName, Object... args) {
        logStep("gestartet", "START", stepName, args, null);
    }

    /** "… Schritt "<name>" durchgeführt …" */
    public static void processStep(String stepName, Object... args) {
        logStep("durchgeführt", "STEP", stepName, args, null);
    }

    /** "… Schritt "<name>" beendet …" */
    public static void processEnd(String stepName, Object... args) {
        logStep("beendet", "END", stepName, args, null);
    }

    /** Freie Info-Nachricht mit demselben Prefix-Schema. */
    public static void info(String msg, Object... args) {
        logMessage("INFO", msg, args, null);
    }

    public static void warn(String msg, Object... args) {
        logMessage("WARN", msg, args, null);
    }

    public static void error(String msg, Throwable t, Object... args) {
        logMessage("ERROR", msg, args, t);
    }

    /* ===== Implementierung ===== */

    private static void logStep(String verb, String tag, String stepName, Object[] args, Throwable t) {
        ensureInit();
        String ts = OffsetDateTime.now().format(TS);
        String name = (args == null || args.length == 0) ? stepName : String.format(stepName, args);
        String line = String.format(
                "%s | Prozess r%d/%d | Schritt \"%s\" %s | PMI-Rank=%d | ClientID=%s | Tag=%s",
                ts, rank, size, name, verb, rank, host, tag
        );
        writeLine(line, t);
    }

    private static void logMessage(String tag, String msg, Object[] args, Throwable t) {
        ensureInit();
        String ts = OffsetDateTime.now().format(TS);
        String text = (args == null || args.length == 0) ? msg : String.format(msg, args);
        String line = String.format(
                "%s | Prozess r%d/%d | PMI-Rank=%d | ClientID=%s | Tag=%s | Nachricht: %s",
                ts, rank, size, rank, host, tag, text
        );
        writeLine(line, t);
    }

    private static void writeLine(String line, Throwable t) {
        synchronized (LOCK) {
            writer.println(line);
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                writer.print(sw.toString());
            }
            writer.flush();
        }
    }

    private static void ensureInit() {
        if (writer == null) {
            throw new IllegalStateException("Logger nicht initialisiert. Log.init(..) vor Nutzung aufrufen.");
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
