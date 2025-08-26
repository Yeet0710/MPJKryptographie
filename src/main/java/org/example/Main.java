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

        // Kommunikationsobjekt
        Intracomm comm = MPI.COMM_WORLD;

        // Rang/Größe
        int rank = comm.Rank();
        int size = comm.Size();

        // Logger initialisieren → schreibt NUR in Datei unter logs/
        Log.init(rank, size);
        Log.processStart("Primzahl-Suche (bitLength=%d)", 1024);
        Log.info("Logdatei: %s", Log.getLogFilePath());

        SecureRandom random = new SecureRandom();
        boolean globalFound = false;
        BigInteger candidate = null;

        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long startTime = System.currentTimeMillis();

        /**
         * Jeder Prozess generiert Kandidaten und prüft per Miller-Rabin.
         * Per Allreduce (MAX) wird verteilt, ob mindestens ein Prozess Erfolg hatte.
         */
        do {
            candidate = new BigInteger(1024, random);
            boolean isPrime = MillerRabin.isProbablePrimeMR(candidate, 20, random);
            sendBuf[0] = isPrime ? 1 : 0;

            String hex = candidate.toString(16);
            String hexShort = hex.substring(0, Math.min(16, hex.length()));
            Log.processStep("Kandidat geprüft: bits=%d, hex=%s..., isPrime=%s",
                    candidate.bitLength(), hexShort, isPrime);

            comm.Allreduce(sendBuf, 0, recvBuf, 0, 1, MPI.INT, MPI.MAX);
            globalFound = (recvBuf[0] == 1);
        } while (!globalFound);

        long endTime = System.currentTimeMillis();

        if (sendBuf[0] == 1) {
            Log.info("Dieser Prozess hat eine probable prime gefunden. bits=%d", candidate.bitLength());
        } else {
            Log.info("Prozess ohne Fund beendet.");
        }

        // Ausgabe der Zeit nur durch Rank 0
        if (rank == 0) {
            Log.warn("Primzahl in %d ms gefunden.", (endTime - startTime));
        }

        // MPI beenden
        MPI.Finalize();

        // Logger sauber beenden
        Log.processEnd("Primzahl-Suche");
        Log.close();
    }
}
