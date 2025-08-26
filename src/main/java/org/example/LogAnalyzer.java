package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die von Log.java erzeugten Logdateien (unter logs/) und berechnet:
 *  - langsamster Prozess (max Dauer Start→Ende)
 *  - Durchlaufzeit je Prozess
 *  - Durchschnittliche Zeit pro Rechner (ClientID/Host)
 *
 * Aufruf:
 *   java -cp bin org.example.LogAnalyzer [optional: /pfad/zu/logs]
 */
public class LogAnalyzer {

    // Logzeilen-Format aus Log.java:
    // <ts> | Prozess r<rank>/<size> | Schritt "<name>" <gestartet|durchgeführt|beendet> | PMI-Rank=<rank> | ClientID=<host> | Tag=<...>
    private static final Pattern STEP_LINE = Pattern.compile(
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+\\-]\\d{2}:\\d{2}) \\| " +
                    "Prozess r(?<rank>\\d+)/(\\d+) \\| Schritt \"(?<name>.+)\" (?<verb>gestartet|durchgeführt|beendet) \\| " +
                    "PMI-Rank=(?<prank>\\d+) \\| ClientID=(?<host>[^|]+) \\ | Tag=(?<tag>[A-Z]+)$".replace("\\ ", "\\s")
    );

    // Variante ohne "Schritt ..." (Info-/Warn-/Error-Zeilen) – hier i. d. R. ignoriert
    private static final Pattern MSG_LINE = Pattern.compile(
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+\\-]\\d{2}:\\d{2}) \\| " +
                    "Prozess r(?<rank>\\d+)/(\\d+) \\| PMI-Rank=(?<prank>\\d+) \\| ClientID=(?<host>[^|]+) \\ | Tag=(?<tag>[A-Z]+) \\| Nachricht: .*"
                            .replace("\\ ", "\\s")
    );

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private record ProcKey(String fileName) {}
    private static class ProcStats {
        String host;
        Integer rank;
        Integer size;
        OffsetDateTime start;
        OffsetDateTime end;

        long durationMs() {
            return (start != null && end != null) ? Duration.between(start, end).toMillis() : -1L;
        }
    }

    public static void main(String[] args) throws IOException {
        Path logsDir = Paths.get(args.length > 0 ? args[0] : System.getProperty("log.dir", "logs"));
        if (!Files.isDirectory(logsDir)) {
            System.err.println("Logs-Verzeichnis nicht gefunden: " + logsDir.toAbsolutePath());
            return;
        }

        // Alle mpj-*.log einsammeln
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(logsDir, "mpj-*.log")) {
            for (Path p : ds) files.add(p);
        }
        if (files.isEmpty()) {
            System.out.println("Keine mpj-*.log Dateien gefunden in: " + logsDir.toAbsolutePath());
            return;
        }

        Map<ProcKey, ProcStats> perProcess = new LinkedHashMap<>();
        for (Path f : files) {
            parseFile(f, perProcess);
        }

        // Nur vollständige (Start+Ende) berücksichtigen
        List<Map.Entry<ProcKey, ProcStats>> complete = perProcess.entrySet().stream()
                .filter(e -> e.getValue().start != null && e.getValue().end != null)
                .toList();

        if (complete.isEmpty()) {
            System.out.println("Keine vollständigen Prozesse (Start & Ende) gefunden.");
            return;
        }

        // Langsamster Prozess
        Map.Entry<ProcKey, ProcStats> slowest = complete.stream()
                .max(Comparator.comparingLong(e -> e.getValue().durationMs()))
                .orElseThrow();

        // Durchschnitt pro Host
        Map<String, List<Long>> byHost = new HashMap<>();
        for (var e : complete) {
            String host = e.getValue().host;
            byHost.computeIfAbsent(host, k -> new ArrayList<>()).add(e.getValue().durationMs());
        }

        // Ausgabe
        System.out.println("=== Log-Analyse ===");
        System.out.println("Verzeichnis: " + logsDir.toAbsolutePath());
        System.out.println("Gefundene Prozess-Logs (vollständig): " + complete.size());
        System.out.println();

        ProcStats sps = slowest.getValue();
        System.out.println("Langsamster Prozess:");
        System.out.printf("  Datei: %s%n", slowest.getKey().fileName);
        System.out.printf("  Host : %s%n", sps.host);
        System.out.printf("  Rank : %d%n", sps.rank);
        System.out.printf("  Size : %d%n", sps.size);
        System.out.printf("  Start: %s%n", sps.start);
        System.out.printf("  Ende : %s%n", sps.end);
        System.out.printf("  Dauer: %s (%d ms)%n", fmtDuration(sps.durationMs()), sps.durationMs());
        System.out.println();

        System.out.println("Durchlaufzeit je Prozess (absteigend nach Dauer):");
        complete.stream()
                .sorted((a, b) -> Long.compare(b.getValue().durationMs(), a.getValue().durationMs()))
                .forEach(e -> {
                    ProcStats ps = e.getValue();
                    System.out.printf("  %-40s | Host=%-20s | r=%-2d/%-2d | Dauer=%-12s | Start=%s | Ende=%s%n",
                            e.getKey().fileName, ps.host, ps.rank, ps.size, fmtDuration(ps.durationMs()), ps.start, ps.end);
                });
        System.out.println();

        System.out.println("Durchschnittliche Zeit pro Rechner:");
        byHost.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey()))
                .forEach(e -> {
                    List<Long> durs = e.getValue();
                    long count = durs.size();
                    double avg = durs.stream().mapToLong(x -> x).average().orElse(0);
                    long min = durs.stream().mapToLong(x -> x).min().orElse(0);
                    long max = durs.stream().mapToLong(x -> x).max().orElse(0);
                    System.out.printf("  Host=%-20s | Läufe=%3d | Ø=%-12s | min=%-12s | max=%-12s%n",
                            e.getKey(), count, fmtDuration((long) avg), fmtDuration(min), fmtDuration(max));
                });
    }

    private static void parseFile(Path file, Map<ProcKey, ProcStats> perProcess) throws IOException {
        ProcKey key = new ProcKey(file.getFileName().toString());
        ProcStats ps = perProcess.computeIfAbsent(key, k -> new ProcStats());

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m1 = STEP_LINE.matcher(line);
                if (m1.matches()) {
                    OffsetDateTime ts = OffsetDateTime.parse(m1.group("ts"), TS);
                    int rank = Integer.parseInt(m1.group("rank"));
                    String host = m1.group("host").trim();
                    String verb = m1.group("verb");
                    String name = m1.group("name");

                    // 'size' steckt in der zweiten Klammer von STEP_LINE – wir extrahieren sie sauber:
                    // workaround: parse erneut "r<rank>/<size>"
                    int size = -1;
                    try {
                        int idxR = line.indexOf("Prozess r");
                        int idxSlash = line.indexOf('/', idxR);
                        int idxSpace = line.indexOf(' ', idxSlash);
                        size = Integer.parseInt(line.substring(idxSlash + 1, idxSpace));
                    } catch (Exception ignored) {}

                    ps.rank = rank;
                    ps.size = size;
                    ps.host = host;

                    if (name.startsWith("Primzahl-Suche")) {
                        if ("gestartet".equals(verb)) {
                            // Nimm die erste Startzeit
                            if (ps.start == null) ps.start = ts;
                        } else if ("beendet".equals(verb)) {
                            // Nimm die letzte Endzeit
                            ps.end = ts;
                        }
                    }
                    continue;
                }

                Matcher m2 = MSG_LINE.matcher(line);
                if (m2.matches()) {
                    // aktuell keine Aggregation nötig; Host/Rank trotzdem aufnehmen
                    int rank = Integer.parseInt(m2.group("rank"));
                    String host = m2.group("host").trim();
                    ps.rank = (ps.rank == null ? rank : ps.rank);
                    ps.host = (ps.host == null ? host : ps.host);
                }
            }
        }
    }

    private static String fmtDuration(long ms) {
        if (ms < 0) return "n/a";
        long hours = ms / 3_600_000;
        ms %= 3_600_000;
        long minutes = ms / 60_000;
        ms %= 60_000;
        long seconds = ms / 1_000;
        ms %= 1_000;
        if (hours > 0) return String.format("%dh %02dm %02ds %03dms", hours, minutes, seconds, ms);
        if (minutes > 0) return String.format("%dm %02ds %03dms", minutes, seconds, ms);
        return String.format("%ds %03dms", seconds, ms);
    }
}
