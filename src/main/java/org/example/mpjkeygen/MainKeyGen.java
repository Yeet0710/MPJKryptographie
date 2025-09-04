package org.example.mpjkeygen;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;
import java.security.SecureRandom;

public class MainKeyGen {
    public static void main(String[] args) throws Exception {
        // ---------------------------------------------------------
        // 0) MPI initialisieren
        // ---------------------------------------------------------
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();
        int size = comm.Size();

        if (rank == 0) {
            System.out.println("=== RSA Key Generation gestartet ===");
            System.out.println("Prozesse insgesamt: " + size);
        }

        // ---------------------------------------------------------
        // 1) Parameter für KeyGen
        // ---------------------------------------------------------
        final int totalBits   = 1024;       // Zielgröße n (z. B. 1024 oder 2048)
        final int mrIterations= 20;         // Genauigkeit Miller–Rabin
        SecureRandom rnd = new SecureRandom();

        if (rank == 0) {
            System.out.println("[Setup] Zielgröße: " + totalBits + " Bit");
            System.out.println("[Setup] Miller-Rabin Runden: " + mrIterations);
        }

        long t0 = System.currentTimeMillis();

        // ---------------------------------------------------------
        // 2) Verteilte Suche nach Primzahlen
        // ---------------------------------------------------------
        if (rank == 0) System.out.println("\n[Phase 1] Suche nach Primzahl p...");
        BigInteger p = PrimeSearch.findPrimeHalfSize(totalBits, mrIterations, comm, rnd);

        if (rank == 0) System.out.println("[Phase 1] p gefunden mit " + p.bitLength() + " Bit.");

        if (rank == 0) System.out.println("\n[Phase 2] Suche nach Primzahl q...");
        BigInteger q = PrimeSearch.findPrimeHalfSize(totalBits, mrIterations, comm, rnd);

        if (rank == 0) System.out.println("[Phase 2] q gefunden mit " + q.bitLength() + " Bit.");

        // ---------------------------------------------------------
        // 3) Ableitung der Schlüsselkomponenten (nur Rank 0)
        // ---------------------------------------------------------
        RsaKeyMaterial km = null;
        if (rank == 0) {
            System.out.println("\n[Phase 3] Berechnung der RSA-Komponenten...");

            if (p.equals(q)) {
                throw new IllegalStateException("p == q; bitte erneut versuchen");
            }

            BigInteger n   = p.multiply(q);
            BigInteger phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
            BigInteger e   = BigInteger.valueOf(65537);

            if (!CryptoMath.sindTeilerfremd(e, phi)) {
                throw new IllegalStateException("gcd(e,phi)!=1; bitte erneut versuchen");
            }

            BigInteger d   = CryptoMath.modInverse(e, phi);
            BigInteger dp  = d.mod(p.subtract(BigInteger.ONE));
            BigInteger dq  = d.mod(q.subtract(BigInteger.ONE));
            BigInteger qInv= CryptoMath.modInverse(q, p);

            km = new RsaKeyMaterial(p, q, n, phi, e, d, dp, dq, qInv);

            System.out.println("[Phase 3] Berechnung abgeschlossen. Modulus n hat " + n.bitLength() + " Bit.");
        }

        // ---------------------------------------------------------
        // 4) Broadcast: KeyMaterial verteilen
        // ---------------------------------------------------------
        Object[] box = new Object[]{ km };
        comm.Bcast(box, 0, 1, MPI.OBJECT, 0);
        km = (RsaKeyMaterial) box[0];

        long t1 = System.currentTimeMillis();

        // ---------------------------------------------------------
// 5) Rank 0 schreibt Schlüsseldateien (Alice & Bob)
// ---------------------------------------------------------
        if (rank == 0) {
            System.out.println("\n[Phase 4] Speichere Schlüsseldateien (Alice & Bob)...");

            // --- Alice: kompletter Schlüssel (öffentlich + privat) ---
            KeyIO.writeAtomically("alice_n.txt",   km.n.toString());
            KeyIO.writeAtomically("alice_e.txt",   km.e.toString());
            KeyIO.writeAtomically("alice_d.txt",   km.d.toString());
            KeyIO.writeAtomically("alice_p.txt",   km.p.toString());
            KeyIO.writeAtomically("alice_q.txt",   km.q.toString());
            KeyIO.writeAtomically("alice_dp.txt",  km.dp.toString());
            KeyIO.writeAtomically("alice_dq.txt",  km.dq.toString());
            KeyIO.writeAtomically("alice_qInv.txt",km.qInv.toString());

            // --- Bob: nur öffentlicher Schlüssel ---
            KeyIO.writeAtomically("bob_n.txt", km.n.toString());
            KeyIO.writeAtomically("bob_e.txt", km.e.toString());
            KeyIO.writeAtomically("bob_d.txt", km.d.toString());

            System.out.println("[Phase 4] Dateien geschrieben:");
            System.out.println("  Alice: n,e,d,p,q,dp,dq,qInv");
            System.out.println("  Bob:   n,e,d");

            System.out.println("\n=== KeyGen erfolgreich abgeschlossen in " + (t1 - t0) + " ms ===");
        }


        // ---------------------------------------------------------
        // 6) MPI beenden
        // ---------------------------------------------------------
        MPI.Finalize();
    }
}
