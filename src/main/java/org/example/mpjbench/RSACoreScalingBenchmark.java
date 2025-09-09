package org.example.mpjbench;

import java.io.IOException;
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
 * Runs {@link RSALibBenchmarkMPI} for process counts from 1 up to six and
 * binds each run to the first <em>np</em> CPU cores. On Windows this is achieved
 * through PowerShell's <code>Start-Process -ProcessorAffinity</code> while on
 * Linux {@code taskset} is used. The benchmark output is parsed to compute
 * average encryption and decryption times and resulting speedups.
 */
public class RSACoreScalingBenchmark {

    private static final Pattern ENC_PATTERN = Pattern.compile(
            "Durchschnittliche Versch.*?zeit\\s*([0-9.,]+) ms");
    private static final Pattern DEC_PATTERN = Pattern.compile(
            "Durchschnittliche Entsch.*?zeit\\s*([0-9.,]+) ms");

    private record Result(int np, double avgMs) {}

    public static void main(String[] args) throws IOException, InterruptedException {
        boolean isWin = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String mpjRun = isWin ? "mpjrun.bat" : "mpjrun.sh";
        int maxProcs = Math.min(6, Runtime.getRuntime().availableProcessors());

        Path logDir = Paths.get("logs");
        Files.createDirectories(logDir);
        List<Result> results = new ArrayList<>();

        for (int np = 1; np <= maxProcs; np++) {
            Path logFile = logDir.resolve("RSACoreScaling_np" + np + ".log");
            if (isWin) {
                int mask = (1 << np) - 1; // use first np cores
                String cmd = String.format(Locale.ROOT,
                        "Start-Process -FilePath '%s' -ArgumentList '-np','%d','-cp','target/classes','%s','--reps','100' -ProcessorAffinity %d -NoNewWindow -Wait -RedirectStandardOutput '%s' -RedirectStandardError '%s'",
                        mpjRun, np, RSALibBenchmarkMPI.class.getName(), mask, logFile.toAbsolutePath(), logFile.toAbsolutePath());
                new ProcessBuilder("powershell", "-Command", cmd).start().waitFor();
            } else {
                String cores = np == 1 ? "0" : "0-" + (np - 1);
                ProcessBuilder pb = new ProcessBuilder(
                        "taskset", "-c", cores,
                        mpjRun, "-np", String.valueOf(np),
                        "-cp", "target/classes",
                        RSALibBenchmarkMPI.class.getName(),
                        "--reps", "100");
                pb.redirectErrorStream(true);
                pb.redirectOutput(logFile.toFile());
                Process proc = pb.start();
                proc.waitFor();
            }

            String content = Files.readString(logFile, StandardCharsets.UTF_8);
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

        if (results.isEmpty()) {
            return;
        }

        double baseline = results.getFirst().avgMs;
        StringBuilder table = new StringBuilder();
        table.append(String.format(Locale.ROOT, "%4s %12s %10s%n", "np", "avg ms", "speedup"));
        for (Result r : results) {
            double speedup = baseline / r.avgMs;
            table.append(String.format(Locale.ROOT, "%4d %12.3f %10.3f%n", r.np, r.avgMs, speedup));
        }
        System.out.print(table);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        Files.writeString(logDir.resolve("RSACoreScalingBenchmark_" + fmt.format(LocalDateTime.now()) + ".log"),
                table.toString(), StandardCharsets.UTF_8);
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}

