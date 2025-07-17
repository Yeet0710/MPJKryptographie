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

        // Rang und Größe erstellen.
        // Rang ist die ID des Prozesses, Größe ist die Anzahl der Prozesse
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        // Erstellen eines SecureRandom-Objekts für Zufallszahlen
        SecureRandom random = new SecureRandom();
        // Variable für die globale Suche nach Primzahlen
        boolean globalFound = false;
        // Variable für den Kandidaten
        BigInteger candidate = null;

        // Buffer für Allreduce
        int[] sendBuf = new int[1];
        int[] recvBuf = new int[1];

        long startTime = System.currentTimeMillis();

        /**
         * Jeder Prozess generiert eine Zufallszahl und prüft, ob sie eine Primzahl ist.
         * Falls dies der Fall ist, wird sendBuf[0] auf 1 gesetzt, andernfalls auf 0.
         *
         * Per Allreduce wird dann der größte Wert von sendBuf (1) über alle Prozesse verteilt.
         * Dadurch wird jeder Prozess darüber informiert, ob mindestens ein Prozess eine Primzahl gefunden hat.
         *
         * Danach wird die globale Variable gesetzt, sodass alle Prozesse beendet werden.
         */
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

         // Die Ausgabe der Zeit erfolgt nur für den Prozess mit Rang 0.
        if (rank == 0) {
            System.out.println("Es wurde eine Primzahl in " + (endTime - startTime) + " ms gefunden.");
        }

        // MPI beenden
        MPI.Finalize();
    }
}
