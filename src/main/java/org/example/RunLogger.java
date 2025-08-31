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
        String fname  = (base.isBlank() ? "mpj-run" : base)
                + "__" + tsPart
                + "__np-" + np
                + ".log";

        Path file = dir.resolve(fname);

        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) {
            // Header
            w.write("MPJ Kryptographie – Laufprotokoll\n");
            w.write("Erzeugt: " + tsPart + System.lineSeparator());
            w.write("Anzahl Prozesse: " + np + System.lineSeparator());
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
                        "Rank %d auf Rechner %s | %s  →  %s | dauer=%d ms | found=%s%n",
                        r.rank, r.host, fmtTs(r.startMs), fmtTs(r.endMs), r.durationMs, r.foundPrime));
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
            w.write("Weitere interessante Daten (Ideen):\n");
            w.write("- Anteil der Prozesse mit Fund\n");
            w.write("- Verteilung p50/p90/p99 der Laufzeiten\n");
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
                            " (" + r.durationMs + " ms); found=" + r.foundPrime
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

        System.out.println("Durchschnitt je Rechner:");
        stats.avgPerHost().forEach((h, avg) ->
                System.out.println("- " + h + ": " + String.format("%.2f ms", avg)));
        System.out.println("=== Ende ===\n");
    }
}
