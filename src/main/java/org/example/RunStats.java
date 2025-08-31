package org.example;

import java.util.*;
import java.util.stream.Collectors;

public class RunStats {

    private final List<ProcessRun> runs;
    private final long globalStart;
    private final long globalEnd;

    public long globalStartMs() { return globalStart; }
    public long globalEndMs()   { return globalEnd;   }


    public RunStats(List<ProcessRun> runs, long globalStart, long globalEnd) {
        this.runs = new ArrayList<>(runs);
        this.globalStart = globalStart;
        this.globalEnd = globalEnd;
        this.runs.sort(Comparator.comparingLong(r -> r.durationMs));
    }

    public long totalRuntimeMs() { return Math.max(0, globalEnd - globalStart); }

    public ProcessRun fastest()  { return runs.isEmpty() ? null : runs.get(0); }

    public ProcessRun slowest()  { return runs.isEmpty() ? null : runs.get(runs.size() - 1); }

    public Map<String, Double> avgPerHost() {
        Map<String, List<ProcessRun>> byHost =
                runs.stream().collect(Collectors.groupingBy(r -> r.host));
        Map<String, Double> res = new TreeMap<>();
        byHost.forEach((h, list) -> {
            double avg = list.stream().mapToLong(r -> r.durationMs).average().orElse(0);
            res.put(h, avg);
        });
        return res;
    }

    public long timeOfWinningProcess() {
        // der Prozess, der tatsächlich die Primzahl gefunden hat (falls vorhanden)
        return runs.stream().filter(r -> r.foundPrime)
                .mapToLong(r -> r.durationMs).min().orElse(-1);
    }

    public List<ProcessRun> runs() { return Collections.unmodifiableList(runs); }
}
