package org.example;

import mpi.*;

import java.math.BigInteger;
import java.security.SecureRandom;

public class Main {

    public static void main(String[] args) throws MPIException {

        // MPI initialisieren
        MPI.Init(args);

        // Kommunkationsobjekt erstellen
        Intracomm comm = MPI.COMM_WORLD;

        // Rang und Größe erstellen
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        SecureRandom random = new SecureRandom();
        boolean globalFound = false;
        BigInteger candidate = null;

        // Buffer für Allreduce
        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long startTime = System.currentTimeMillis();

        do {
            // Neuer Kandidat und Test auf Primzahl
            candidate = new BigInteger(1024, random);
            boolean isPrime = MillerRabin.isProbablePrimeMR(candidate, 20, random);
            sendBuf[0] = isPrime ? 1 : 0;

            comm.Allreduce(sendBuf, 0, recvBuf, 0, 1, MPI.INT, MPI.MAX);

            globalFound = recvBuf[0] == 1;
        } while (!globalFound);

        long endTime = System.currentTimeMillis();

        if (sendBuf[0] == 1) {
            System.out.println("Process " + rank + " found a probable prime: " + candidate);
        } else {
            System.out.println("Process " + rank + " did not find a prime.");
        }

        if (rank == 0) {
            System.out.println("Es wurde eine Primzahl in " + (endTime - startTime) + " ms gefunden.");
        }

        // MPI beenden
        MPI.Finalize();
    }
}
