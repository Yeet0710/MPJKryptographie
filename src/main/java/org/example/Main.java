package org.example;

import mpi.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;

public class Main {

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;

        int rank = comm.Rank();
        int size = comm.Size();
        String host = InetAddress.getLocalHost().getHostName();

        // --- Konfiguration ---
        final int CAND_BITS = 1024;       // gewünschte Bitlänge für Kandidaten
        final int MR_ROUNDS = 20;         // Miller-Rabin Iterationen

        LogicalTime ltime = new LogicalTime(rank);
        SecureRandom random = new SecureRandom();

        boolean globalFound = false;
        BigInteger candidate;

        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long globalStart = System.currentTimeMillis();
        long localStart  = globalStart;

        // Logische Zeiten
        long ltsStart = ltime.tick();
        long ltsFound = -1L;
        int  bitsActual = -1;

        do {
            // Kandidat erzeugen und prüfen
            candidate = new BigInteger(CAND_BITS, random);
            boolean isPrime = MillerRabin.isProbablePrimeMR(candidate, MR_ROUNDS, random);
            sendBuf[0] = isPrime ? 1 : 0;

            // LTS: lokales Prüfergebnis erzeugt
            ltime.tick();

            // Alle informieren, ob jemand eine Primzahl fand
            comm.Allreduce(sendBuf, 0, recvBuf, 0, 1, MPI.INT, MPI.MAX);

            // LTS: Kollektiv beendet
            ltime.tick();

            globalFound = (recvBuf[0] == 1);
            if (isPrime && ltsFound < 0) {
                // LTS: Fund markiert
                ltsFound = ltime.tick();
                bitsActual = candidate.bitLength();
            }
        } while (!globalFound);

        long localEnd = System.currentTimeMillis();
        boolean iFound = (sendBuf[0] == 1);

        if (iFound) {
            System.out.println("Process " + rank + " found a probable prime: " + candidate);
        } else {
            System.out.println("Process " + rank + " did not find a prime.");
        }

        // ---- Logging-Daten sammeln & zu Rank 0 senden ----
        // LTS: vor dem Gather noch einmal ticken (Sendevorbereitung)
        ltime.tick();

        ProcessRun myRun = new ProcessRun(
                rank, host, localStart, localEnd, iFound,
                ltsStart, ltsFound, /* ltsEnd wird später gesetzt */ -1L,
                CAND_BITS, bitsActual
        );

        Object[] sendArr = new Object[] { myRun };
        Object[] recvArr = new Object[comm.Size()];   // auf ALLEN RANKS anlegen!

        comm.Gather(sendArr, 0, 1, MPI.OBJECT,
                recvArr, 0, 1, MPI.OBJECT, 0);

        // Alle synchronisieren
        comm.Barrier();

        // LTS: Prozessende
        long ltsEnd = ltime.tick();

        // ltsEnd separat an Rank 0 übertragen
        long[] endLtsBuf = new long[]{ ltsEnd };
        long[] allEndLts = new long[size];
        comm.Gather(endLtsBuf, 0, 1, MPI.LONG, allEndLts, 0, 1, MPI.LONG, 0);

        long globalEnd = System.currentTimeMillis();

        if (rank == 0) {
            // Liste der Runs aufbauen und ltsEnd injizieren
            java.util.List<ProcessRun> runs = new java.util.ArrayList<>(recvArr.length);
            for (int i = 0; i < recvArr.length; i++) {
                ProcessRun r = (ProcessRun) recvArr[i];
                runs.add(new ProcessRun(
                        r.rank, r.host, r.startMs, r.endMs, r.foundPrime,
                        r.ltsStart, r.ltsFound, allEndLts[i],
                        r.bitsRequested, r.bitsActual
                ));
            }

            RunStats stats = new RunStats(runs, globalStart, globalEnd);

            RunLogger.printConsoleSummary(stats);
            var path = RunLogger.writeLog(stats, "mpj-run");
            System.out.println("Logdatei geschrieben: " + path.toAbsolutePath());
        }

        MPI.Finalize();
    }
}
