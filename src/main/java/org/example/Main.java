package org.example;

import mpi.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;

public class Main {

    public static void main(String[] args) throws Exception {
        MPI.Init(args);

        // Standardwerte (via CLI überschreibbar)
        int bitLength = 1024;     // -bitlength=2048
        int mrIterations = 20;    // -mriterationen=25
        for (String arg : args) {
            if (arg.startsWith("-bitlength=")) {
                bitLength = Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
            } else if (arg.startsWith("-mriterationen=")) {
                mrIterations = Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
            }
        }

        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();
        int size = comm.Size();
        String host = InetAddress.getLocalHost().getHostName();

        LogicalTime ltime = new LogicalTime(rank);
        SecureRandom random = new SecureRandom();

        boolean globalFound = false;
        BigInteger candidate;

        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long globalStart = System.currentTimeMillis();
        long localStart  = globalStart;

        // Logische Zeiten & Bitlängen
        long ltsStart = ltime.tick();
        long ltsFound = -1L;
        int  bitsActual = -1;

        do {
            // Kandidat erzeugen und prüfen
            candidate = new BigInteger(bitLength, random);
            boolean isPrime = MillerRabin.isProbablePrimeMR(candidate, mrIterations, random);
            sendBuf[0] = isPrime ? 1 : 0;

            // LTS: lokales Prüfergebnis erzeugt
            ltime.tick();

            // Kollektive Info, ob jemand gefunden hat
            comm.Allreduce(sendBuf, 0, recvBuf, 0, 1, MPI.INT, MPI.MAX);

            // LTS: Kollektiv beendet
            ltime.tick();

            globalFound = (recvBuf[0] == 1);
            if (isPrime && ltsFound < 0) {
                // LTS: Fund markiert + tatsächliche Bitlänge
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
                ltsStart, ltsFound, /* ltsEnd folgt */ -1L,
                bitLength, bitsActual
        );

        // Für MPI.OBJECT Object-Arrays nutzen
        Object[] sendArr = new Object[] { myRun };
        Object[] recvArr = new Object[size];   // auf ALLEN RANKS anlegen!

        comm.Gather(sendArr, 0, 1, MPI.OBJECT,
                recvArr, 0, 1, MPI.OBJECT, 0);

        // Alle synchronisieren
        comm.Barrier();

        // LTS: Prozessende
        long ltsEnd = ltime.tick();

        // ltsEnd separat an Rank 0 übertragen (LONG)
        long[] endLtsBuf = new long[]{ ltsEnd };
        long[] allEndLts = new long[size];
        comm.Gather(endLtsBuf, 0, 1, MPI.LONG,
                allEndLts, 0, 1, MPI.LONG, 0);

        long globalEnd = System.currentTimeMillis();

        if (rank == 0) {
            // Recv-Array in ProcessRun-Liste casten und ltsEnd injizieren
            java.util.List<ProcessRun> runs = new java.util.ArrayList<>(recvArr.length);
            for (int i = 0; i < recvArr.length; i++) {
                ProcessRun r0 = (ProcessRun) recvArr[i];
                runs.add(new ProcessRun(
                        r0.rank, r0.host, r0.startMs, r0.endMs, r0.foundPrime,
                        r0.ltsStart, r0.ltsFound, allEndLts[i],
                        r0.bitsRequested, r0.bitsActual
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
