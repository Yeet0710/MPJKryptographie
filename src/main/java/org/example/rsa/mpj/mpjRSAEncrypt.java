package org.example.rsa.mpj;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.example.rsa.RSAUTF8;
import org.example.rsa.RSAUtils;
import org.example.mpjkeygen.schnelleExponentiation;

/**
 * MPI-Wrapper: verteilt RSA-Verschlüsselung (Block-weise) über mehrere Prozesse.
 * Ausgabe: Base64-Chiffrat wird in eine Datei geschrieben (default "cipher.txt" oder letzte .txt-Arg).
 *
 * Aufrufbeispiele (PowerShell):
 *   # schreibt in cipher.txt
 *   mpjrun.bat -dev multicore -np 4 -cp ".;JAR;mpj.jar" org.example.rsa.mpj.mpjRSAEncrypt "Möge die Macht mit dir sein!"
 *
 *   # schreibt in myCipher.txt
 *   mpjrun.bat -dev multicore -np 4 -cp ".;JAR;mpj.jar" org.example.rsa.mpj.mpjRSAEncrypt "Möge die Macht mit dir sein!" "myCipher.txt"
 */
public class mpjRSAEncrypt {

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();
        int size = comm.Size();

        // -------- Argumente robust parsen (MPJ hängt eigene Tokens vorn an) --------
        String message = "Hello from MPI RSA!";
        String outFile = "cipher.txt";
        if (args != null && args.length > 0) {
            String last = args[args.length - 1];
            if (last.toLowerCase().endsWith(".txt")) {
                outFile = last;
                if (args.length >= 2) {
                    message = args[args.length - 2];
                }
            } else {
                message = last;
            }
        }

        BigInteger e = null, n = null;
        BigInteger[] blocksArr = null;
        int total = 0;

        long t0 = System.currentTimeMillis();

        if (rank == 0) {
            // Schlüssel laden und Message zu Blöcken
            RSAUtils.loadKeysFromFiles();

            e = RSAUtils.getBobPublicKey();
            n = RSAUtils.getBobModulus();

            List<BigInteger> blocks = RSAUTF8.textToBigIntegerBlocks(message, n);
            blocksArr = blocks.toArray(new BigInteger[0]);
            total = blocksArr.length;

            System.out.println("[Encrypt][Rank0] Blöcke gesamt: " + total + " | Prozesse: " + size);
        }

        // --- Meta broadcasten ---
        int[] metaTotal = new int[]{ total };
        comm.Bcast(metaTotal, 0, 1, MPI.INT, 0);
        total = metaTotal[0];

        String[] keyMeta = new String[2];
        if (rank == 0) {
            keyMeta[0] = e.toString();
            keyMeta[1] = n.toString();
        }
        comm.Bcast(keyMeta, 0, 2, MPI.OBJECT, 0);
        e = new BigInteger(keyMeta[0]);
        n = new BigInteger(keyMeta[1]);

        // --- Blöcke broadcasten ---
        if (rank != 0) {
            blocksArr = new BigInteger[total];
        }
        if (total > 0) {
            comm.Bcast(blocksArr, 0, total, MPI.OBJECT, 0);
        }

        // --- Lokale Round-Robin-Verschlüsselung ---
        List<Integer> idx = new ArrayList<>();
        List<BigInteger> vals = new ArrayList<>();
        for (int i = rank; i < total; i += size) {
            BigInteger c = schnelleExponentiation.pow(blocksArr[i], e, n);
            idx.add(i);
            vals.add(c);
        }

        int localCount = idx.size();
        int[] idxArr = new int[localCount];
        for (int i = 0; i < localCount; i++) idxArr[i] = idx.get(i);
        BigInteger[] valArr = vals.toArray(new BigInteger[0]);

        // --- Ergebnisse zu Rank 0 ---
        if (rank == 0) {
            BigInteger[] result = new BigInteger[total];

            // eigene Ergebnisse
            for (int i = 0; i < localCount; i++) {
                result[idxArr[i]] = valArr[i];
            }

            // von anderen Ranks empfangen
            for (int r = 1; r < size; r++) {
                int[] cArr = new int[1];
                comm.Recv(cArr, 0, 1, MPI.INT, r, 100);
                int c = cArr[0];

                int[] ridx = new int[c];
                if (c > 0) comm.Recv(ridx, 0, c, MPI.INT, r, 101);

                BigInteger[] rvals = new BigInteger[c];
                if (c > 0) comm.Recv(rvals, 0, c, MPI.OBJECT, r, 102);

                for (int j = 0; j < c; j++) result[ridx[j]] = rvals[j];
            }

            List<BigInteger> encrypted = Arrays.asList(result);
            String base64 = RSAUTF8.blocksToBase64String(encrypted, n);

            // In Datei schreiben (UTF-8)
            Files.writeString(Path.of(outFile), base64, StandardCharsets.UTF_8);
            System.out.println("[Encrypt][Rank0] Chiffrat gespeichert in: " + outFile);

            long t1 = System.currentTimeMillis();
            System.out.println("[Encrypt][Rank0] Fertig in " + (t1 - t0) + " ms.");
            System.out.println("\n=== Base64-Chiffrat (Alice→Bob) ===\n" + base64);
        } else {
            // zu Rank 0 senden
            comm.Send(new int[]{localCount}, 0, 1, MPI.INT, 0, 100);
            if (localCount > 0) {
                comm.Send(idxArr, 0, localCount, MPI.INT, 0, 101);
                comm.Send(valArr, 0, localCount, MPI.OBJECT, 0, 102);
            }
        }

        MPI.Finalize();
    }
}
