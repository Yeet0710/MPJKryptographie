package org.example;

import mpi.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;

        int rank = comm.Rank();
        int size = comm.Size();
        String host = InetAddress.getLocalHost().getHostName();

        SecureRandom random = new SecureRandom();
        boolean globalFound = false;
        BigInteger candidate;

        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long globalStart = System.currentTimeMillis();
        long localStart = globalStart;

        do {
            candidate = new BigInteger(1024, random);
            boolean isPrime = MillerRabin.isProbablePrimeMR(candidate, 20, random);
            sendBuf[0] = isPrime ? 1 : 0;

            // informiert alle Prozesse, ob irgendwer eine Primzahl gefunden hat
            comm.Allreduce(sendBuf, 0, recvBuf, 0, 1, MPI.INT, MPI.MAX);
            globalFound = (recvBuf[0] == 1);
        } while (!globalFound);

        long localEnd = System.currentTimeMillis();
        boolean iFound = (sendBuf[0] == 1);

        if (iFound) {
            System.out.println("Process " + rank + " found a probable prime: " + candidate);
        } else {
            System.out.println("Process " + rank + " did not find a prime.");
        }

// ---- Logging-Daten sammeln & zu Rank 0 senden ----
        ProcessRun myRun = new ProcessRun(rank, host, localStart, localEnd, iFound);

// Für MPI.OBJECT am sichersten Object-Arrays verwenden
        Object[] sendArr = new Object[] { myRun };
        Object[] recvArr = new Object[comm.Size()];   // WICHTIG: auf ALLEN RANKS anlegen!

        comm.Gather(sendArr, 0, 1, MPI.OBJECT,
                recvArr, 0, 1, MPI.OBJECT, 0);

// Alle synchronisieren, dann globale Endzeit nehmen
        comm.Barrier();
        long globalEnd = System.currentTimeMillis();

        if (rank == 0) {
            // Recv-Array in ProcessRun-Liste casten
            java.util.List<ProcessRun> runs = new java.util.ArrayList<>(recvArr.length);
            for (Object o : recvArr) runs.add((ProcessRun) o);

            RunStats stats = new RunStats(runs, globalStart, globalEnd);

            RunLogger.printConsoleSummary(stats);
            var path = RunLogger.writeLog(stats, "mpj-run");
            System.out.println("Logdatei geschrieben: " + path.toAbsolutePath());
        }

        MPI.Finalize();

    }
}
