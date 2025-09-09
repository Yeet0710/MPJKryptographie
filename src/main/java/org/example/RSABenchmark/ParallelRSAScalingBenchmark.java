package org.example.RSABenchmark;

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
 * Runs {@link ParallelRSABenchmark} for different process counts and
 * prints a table with total runtime and speedup based on encryption
 * plus decryption times.
 */
public class ParallelRSAScalingBenchmark {

    private static final Pattern ENC_PATTERN =
            Pattern.compile("Versch.*?:\\s*([0-9.,]+) ms");
    private static final Pattern DEC_PATTERN =
            Pattern.compile("Entsch.*?:\\s*([0-9.,]+) ms");

    private record Result(int np, double totalMs) {}

    public static void main(String[] args) throws IOException, InterruptedException {
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
                    ParallelRSABenchmark.class.getName());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append(System.lineSeparator());
                }
            }
            proc.waitFor();

            String content = out.toString();
            Files.writeString(logDir.resolve("ParallelRSAScalingBenchmark_np" + np + ".log"),
                    content, StandardCharsets.UTF_8);

            Matcher em = ENC_PATTERN.matcher(content);
            Matcher dm = DEC_PATTERN.matcher(content);
            if (em.find() && dm.find()) {
                double enc = parseDouble(em.group(1));
                double dec = parseDouble(dm.group(1));
                results.add(new Result(np, enc + dec));
            }
        }

        if (results.isEmpty()) return;

        double baseline = results.get(0).totalMs;
        StringBuilder table = new StringBuilder();
        table.append(String.format(Locale.ROOT, "%4s %12s %10s%n", "np", "total ms", "speedup"));
        for (Result r : results) {
            double speed = baseline / r.totalMs;
            table.append(String.format(Locale.ROOT, "%4d %12.3f %10.3f%n", r.np, r.totalMs, speed));
        }

        System.out.print(table);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        Files.writeString(logDir.resolve("ParallelRSAScalingBenchmark_" + fmt.format(LocalDateTime.now()) + ".log"),
                table.toString(), StandardCharsets.UTF_8);
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}

