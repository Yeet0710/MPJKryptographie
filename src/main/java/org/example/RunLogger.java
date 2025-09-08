package org.example;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class RunLogger {

    // ---------- Helpers ----------
    private static String fmtDuration(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        long mm = m % 60;
        long ss = s % 60;
        long msRem = ms % 1000;
        if (h > 0) return String.format("%d:%02d:%02d.%03d", h, mm, ss, msRem);
        if (m > 0) return String.format("%d:%02d.%03d", m, ss, msRem);
        return String.format("%d.%03d s", s, msRem);
    }

    // Lokale Zeit ohne Zeitzone/Offset
    private static final DateTimeFormatter TS_FMT_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static String fmtTs(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(TS_FMT_LOCAL);
    }

    // Dateiname-sicher machen
    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "";
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    // ---------- Public API ----------
    public static Path writeLog(RunStats stats, String filePrefix) throws IOException {
        Path dir = Paths.get(System.getProperty("user.dir"), "logs");
        Files.createDirectories(dir);

        List<ProcessRun> runs = stats.runs();
        int np = runs.size();

        String tsPart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String base   = sanitize(filePrefix);
        String bitsPart = runs.isEmpty() ? "" : "__bits-" + runs.get(0).bitsRequested;
        String fname  = (base.isBlank() ? "mpj-run" : base)
                + "__" + tsPart
                + "__np-" + np
                + bitsPart
                + ".log";

        Path file = dir.resolve(fname);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) {
            // Header
            w.write("MPJ Kryptographie – Laufprotokoll\n");
            w.write("Erzeugt: " + tsPart + System.lineSeparator());
            w.write("Anzahl Prozesse: " + np + System.lineSeparator());
            w.write(System.lineSeparator());

            // Konfiguration
            w.write("Konfiguration:\n");
            w.write("- Kandidaten-Bitlänge (requested): " +
                    (runs.isEmpty() ? "n/a" : runs.get(0).bitsRequested) + System.lineSeparator());
            w.write(System.lineSeparator());

            // Globales Zeitfenster
            String startTs = fmtTs(stats.globalStartMs());
            String endTs   = fmtTs(stats.globalEndMs());
            w.write("Zeitfenster (lokale Systemzeit):\n");
            w.write("Start  : " + startTs + System.lineSeparator());
            w.write("Ende   : " + endTs + System.lineSeparator());
            w.write("Gesamt : " + fmtDuration(stats.totalRuntimeMs()) + System.lineSeparator());
            w.write(System.lineSeparator());

            // Prozess-Details
            w.write("Prozess-Details:\n");
            for (ProcessRun r : runs) {
                w.write(String.format(
                        "Rank %d @ %s | %s  →  %s | dauer=%d ms | found=%s | bits=%s | LTS(start=%s, found=%s, end=%s)%n",
                        r.rank, r.host, fmtTs(r.startMs), fmtTs(r.endMs), r.durationMs, r.foundPrime,
                        (r.bitsActual >= 0 ? (r.bitsActual + "/" + r.bitsRequested) : ("-/" + r.bitsRequested)),
                        LogicalTime.fmt(r.ltsStart),
                        (r.ltsFound >= 0 ? LogicalTime.fmt(r.ltsFound) : "-"),
                        LogicalTime.fmt(r.ltsEnd)
                ));
            }
            w.write(System.lineSeparator());

            // Zusammenfassung
            w.write("Zusammenfassung:\n");
            w.write("Gesamtzeit: " + fmtDuration(stats.totalRuntimeMs()) + System.lineSeparator());

            ProcessRun slow = stats.slowest();
            ProcessRun fast = stats.fastest();
            if (slow != null) w.write("Langsamster Prozess: Rank " + slow.rank + " @ " + slow.host +
                    " (" + fmtDuration(slow.durationMs) + ")\n");
            if (fast != null) w.write("Schnellster Prozess: Rank " + fast.rank + " @ " + fast.host +
                    " (" + fmtDuration(fast.durationMs) + ")\n");

            long winning = stats.timeOfWinningProcess();
            if (winning >= 0)
                w.write("Zeit des durchlaufenden (findenden) Prozesses: " + fmtDuration(winning) + "\n");

            w.write(System.lineSeparator());
            w.write("Durchschnittliche Zeiten pro Rechner:\n");
            for (Map.Entry<String, Double> e : stats.avgPerHost().entrySet()) {
                w.write(String.format("- %s: %.2f ms%n", e.getKey(), e.getValue()));
            }

            w.write(System.lineSeparator());
            w.write("Globale logische Reihenfolge (nach LTS-Ende):\n");
            runs.stream()
                    .sorted((a,b) -> Long.compare(a.ltsEnd, b.ltsEnd))
                    .forEachOrdered(r -> {
                        try {
                            w.write(String.format("- %-10s  Rank %d @ %s  (dauer=%d ms)%n",
                                    LogicalTime.fmt(r.ltsEnd), r.rank, r.host, r.durationMs));
                        } catch (IOException ex) { /* ignore */ }
                    });

        }

        return file;
    }

    // ---------- Console summary ----------
    public static void printConsoleSummary(RunStats stats) {
        List<ProcessRun> runs = stats.runs();
        System.out.println("\n=== Lauf-Zusammenfassung (Rank 0) ===");
        System.out.println("n Prozesse gestartet: " + runs.size());
        System.out.println("Start: " + fmtTs(stats.globalStartMs()));
        System.out.println("Ende : " + fmtTs(stats.globalEndMs()));
        System.out.println("Gesamt: " + fmtDuration(stats.totalRuntimeMs()));

        for (ProcessRun r : runs) {
            System.out.println(
                    "Rank " + r.rank + " @ " + r.host +
                            " -> " + fmtTs(r.startMs) + " → " + fmtTs(r.endMs) +
                            " (" + r.durationMs + " ms); found=" + r.foundPrime +
                            "; bits=" + (r.bitsActual >= 0 ? (r.bitsActual + "/" + r.bitsRequested) : ("-/" + r.bitsRequested)) +
                            "; LTS(start=" + LogicalTime.fmt(r.ltsStart) +
                            ", found=" + (r.ltsFound >= 0 ? LogicalTime.fmt(r.ltsFound) : "-") +
                            ", end=" + LogicalTime.fmt(r.ltsEnd) + ")"
            );
        }

        ProcessRun slow = stats.slowest();
        ProcessRun fast = stats.fastest();
        if (slow != null)
            System.out.println("Langsamster: Rank " + slow.rank + " @ " + slow.host +
                    " (" + slow.durationMs + " ms)");
        if (fast != null)
            System.out.println("Schnellster: Rank " + fast.rank + " @ " + fast.host +
                    " (" + fast.durationMs + " ms)");

        long winning = stats.timeOfWinningProcess();
        if (winning >= 0)
            System.out.println("Zeit des findenden Prozesses: " + fmtDuration(winning));

        System.out.println("Logische Reihenfolge (LTS end):");
        stats.runs().stream()
                .sorted((a,b) -> Long.compare(a.ltsEnd, b.ltsEnd))
                .forEach(r -> System.out.println("  " + LogicalTime.fmt(r.ltsEnd) +
                        "  -> Rank " + r.rank + " @ " + r.host));
        System.out.println("=== Ende ===\n");
    }
}
