package org.example;

import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

import java.math.BigInteger;
import java.security.SecureRandom;

public class Main {

    public static void main(String[] args) throws MPIException {

        // MPI initialisieren
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;

        int rank = comm.Rank();
        int size = comm.Size();

        // Logger initialisieren (Datei)
        Log.init(rank, size);
        Log.processStart("Primzahl-Suche (bitLength=%d)", 1024);

        SecureRandom random = new SecureRandom();
        boolean globalFound = false;
        BigInteger candidate;

        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long startTime = System.currentTimeMillis();

        do {
            candidate = new BigInteger(1024, random);
            long t0 = System.currentTimeMillis();
            boolean isPrime = MillerRabin.isProbablePrimeMR(candidate, 20, random);
            long t1 = System.currentTimeMillis();

            sendBuf[0] = isPrime ? 1 : 0;

            // Schritt-Log ohne Rohdaten, nur Kontext
            Log.processStep("Kandidat geprüft (bits=%d, mrRounds=%d, isPrime=%s, t_ms=%d)",
                    candidate.bitLength(), 20, isPrime, (t1 - t0));

            comm.Allreduce(sendBuf, 0, recvBuf, 0, 1, MPI.INT, MPI.MAX);
            globalFound = (recvBuf[0] == 1);
        } while (!globalFound);

        long endTime = System.currentTimeMillis();

        if (sendBuf[0] == 1) {
            Log.info("Fund: probable prime (bits=%d).", 1024);
        } else {
            Log.info("Kein Fund in diesem Prozess.");
        }

        if (rank == 0) {
            Log.warn("Primzahl global gefunden in %d ms.", (endTime - startTime));
        }

        MPI.Finalize();
        Log.processEnd("Primzahl-Suche");
        Log.close();
    }
}
