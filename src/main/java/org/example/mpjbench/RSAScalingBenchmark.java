package org.example.mpjbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs {@link RSALibBenchmarkMPI} for different process counts and
 * calculates speedups based on the reported average encryption and
 * decryption times.
 */
public class RSAScalingBenchmark {

    // Die vom MPJ-Runtime erzeugten Logdateien sind nicht immer in UTF-8
    // kodiert. Beim Einlesen mit UTF-8 entstehen daher Ersatzzeichen (�)
    // anstelle der Umlaute, wodurch die exakten Strings mit "Verschlüsselungs"
    // und "Entschlüsselungs" nicht mehr erkannt werden. Die vorherigen,
    // sehr spezifischen Regex-Ausdrücke schlugen deshalb fehl und die Liste
    // der gemessenen Zeiten blieb leer, was wiederum zu NaN in der Tabelle
    // führte. Statt den gesamten Wortlaut mit Umlauten zu matchen, suchen wir
    // nun nur nach stabilen Textfragmenten und erlauben beliebige Zeichen
    // (auch Ersatzzeichen) dazwischen.
    private static final Pattern ENC_PATTERN = Pattern.compile(
            "Durchschnittliche Versch.*?zeit:\\s*([0-9.,]+) ms");
    private static final Pattern DEC_PATTERN = Pattern.compile(
            "Durchschnittliche Entsch.*?zeit:\\s*([0-9.,]+) ms");

    private record Result(int np, double avgMs) {}

    public static void main(String[] args) throws IOException, InterruptedException {
        // determine np values 1,2,4,... up to number of available processors
        int maxProcs = Runtime.getRuntime().availableProcessors();
        List<Integer> npValues = new ArrayList<>();
        for (int np = 1; np <= maxProcs; np *= 2) {
            npValues.add(np);
        }

        List<Result> results = new ArrayList<>();
        Path logDir = Paths.get("logs");
        Files.createDirectories(logDir);

        String mpjRun = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "mpjrun.bat" : "mpjrun.sh";

        for (int np : npValues) {
            ProcessBuilder pb = new ProcessBuilder(
                    mpjRun, "-np", String.valueOf(np),
                    "-cp", "target/classes",
                    RSALibBenchmarkMPI.class.getName(),
                    "--reps", "100");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append(System.lineSeparator());
                }
            }
            proc.waitFor();

            String content = out.toString();
            Files.writeString(logDir.resolve("RSAScalingBenchmark_np" + np + ".log"), content, StandardCharsets.UTF_8);

            Matcher em = ENC_PATTERN.matcher(content);
            Matcher dm = DEC_PATTERN.matcher(content);
            List<Double> times = new ArrayList<>();
            while (em.find() && dm.find()) {
                double enc = parseDouble(em.group(1));
                double dec = parseDouble(dm.group(1));
                times.add(enc + dec);
            }
            double avg = times.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            results.add(new Result(np, avg));
        }

        if (results.isEmpty()) return;

        double baseline = results.getFirst().avgMs;
        StringBuilder table = new StringBuilder();
        table.append(String.format(Locale.ROOT, "%4s %12s %10s%n", "np", "avg ms", "speedup"));
        for (Result r : results) {
            double speedup = baseline / r.avgMs;
            table.append(String.format(Locale.ROOT, "%4d %12.3f %10.3f%n", r.np, r.avgMs, speedup));
        }

        System.out.print(table);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        Files.writeString(logDir.resolve("RSAScalingBenchmark_" + fmt.format(LocalDateTime.now()) + ".log"),
                table.toString(), StandardCharsets.UTF_8);
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}

