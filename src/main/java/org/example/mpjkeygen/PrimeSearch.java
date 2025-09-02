package org.example.mpjkeygen;

import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

import java.math.BigInteger;
import java.security.SecureRandom;

public final class PrimeSearch {

    /**
     * Sucht verteilte Primzahlen:
     * - Jeder Prozess erzeugt 1 Kandidaten pro Runde (halbe Bitlänge von n).
     * - Prüft mit Miller-Rabin.
     * - Allgather sammelt alle Kandidaten (null, wenn nicht prime).
     * - Gewinner = erste nicht-null Position (niedrigstes Rank mit Fund).
     */
    public static BigInteger findPrimeHalfSize(
            int totalBits, int iterations,
            Intracomm comm, SecureRandom rnd) throws MPIException {

        final int bits = totalBits / 2;
        final int size = comm.Size();
        final int rank = comm.Rank();

        BigInteger winner = null;

        // Runden drehen, bis einer gefunden wurde
        while (winner == null) {

            // 1) Kandidat erzeugen (richtige Bitlänge & ungerade)
            BigInteger cand = new BigInteger(bits, rnd)
                    .setBit(bits - 1)
                    .setBit(0);

            // 2) Primalitätstest
            boolean ok = MillerRabin.isProbablePrimeMR(cand, iterations, rnd);
            if (ok) {
                System.out.println("[Rank " + comm.Rank() + "] hat eine Primzahl gefunden: "
                        + cand.bitLength() + " Bit");
            }


            // 3) Alle senden ihren (ggf. null-)Kandidaten
            Object[] send = new Object[]{ ok ? cand : null };
            Object[] recv = new Object[size];

            comm.Allgather(send, 0, 1, MPI.OBJECT,
                    recv, 0, 1, MPI.OBJECT);

            // 4) Gewinner deterministisch auswählen (erste nicht-null → kleinstes Rank)
            for (Object o : recv) {
                if (o != null) {
                    winner = (BigInteger) o;
                    break;
                }
            }
            // Falls in dieser Runde niemand fündig: winner bleibt null → nächste Runde.
        }

        return winner; // identisch auf allen Ranks
    }
}
