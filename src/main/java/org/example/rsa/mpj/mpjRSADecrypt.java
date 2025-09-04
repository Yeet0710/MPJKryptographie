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
 * MPI-Wrapper: verteilt RSA-Entschlüsselung (Block-weise) über mehrere Prozesse.
 * Eingabe: Base64 aus Datei (default "cipher.txt" oder letzte .txt-Arg) ODER direktes Base64 als letztes Arg.
 *
 * Aufrufbeispiele (PowerShell):
 *   # liest aus cipher.txt
 *   mpjrun.bat -dev multicore -np 4 -cp ".;JAR;mpj.jar" org.example.rsa.mpj.mpjRSADecrypt
 *
 *   # liest aus myCipher.txt
 *   mpjrun.bat -dev multicore -np 4 -cp ".;JAR;mpj.jar" org.example.rsa.mpj.mpjRSADecrypt "myCipher.txt"
 *
 *   # direktes Base64
 *   mpjrun.bat -dev multicore -np 4 -cp ".;JAR;mpj.jar" org.example.rsa.mpj.mpjRSADecrypt "BASE64..."
 */
public class mpjRSADecrypt {

    private static boolean looksLikeBase64(String s) {
        if (s == null) return false;
        // Sehr grobe Heuristik – reicht für unsere CLI-Zwecke
        return s.matches("^[A-Za-z0-9+/=]+$") && s.length() >= 4;
    }

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();
        int size = comm.Size();

        String base64 = null;

        if (rank == 0) {
            // -------- Argumente robust parsen --------
            String inFile = "cipher.txt";
            if (args != null && args.length > 0) {
                String last = args[args.length - 1];
                if (last.toLowerCase().endsWith(".txt")) {
                    inFile = last;
                    System.out.println("[Decrypt][Rank0] Lese Chiffrat aus Datei: " + inFile);
                    base64 = Files.readString(Path.of(inFile), StandardCharsets.UTF_8).trim();
                } else if (looksLikeBase64(last)) {
                    base64 = last;
                    System.out.println("[Decrypt][Rank0] Base64 aus Argument übernommen.");
                } else {
                    System.out.println("[Decrypt][Rank0] Argument nicht erkannt – lese aus Standarddatei: " + inFile);
                    base64 = Files.readString(Path.of(inFile), StandardCharsets.UTF_8).trim();
                }
            } else {
                System.out.println("[Decrypt][Rank0] Keine Argumente – lese aus Standarddatei: " + inFile);
                base64 = Files.readString(Path.of(inFile), StandardCharsets.UTF_8).trim();
            }
        }

        // Broadcast „haben wir Base64?“
        int[] hasB64 = new int[]{ (rank == 0 && base64 != null && !base64.isEmpty()) ? 1 : 0 };
        comm.Bcast(hasB64, 0, 1, MPI.INT, 0);
        if (hasB64[0] == 0) {
            if (rank == 0) System.err.println("Kein Base64-Chiffrat verfügbar.");
            MPI.Finalize();
            return;
        }

        BigInteger d = null, n = null;
        BigInteger[] blocksArr = null;
        int total = 0;

        long t0 = System.currentTimeMillis();

        if (rank == 0) {
            // Schlüssel + Blöcke vorbereiten
            RSAUtils.loadKeysFromFiles();
            d = RSAUtils.getBobPrivateKey();  // setzt bob_d.txt voraus
            n = RSAUtils.getBobModulus();

            List<BigInteger> blocks = RSAUTF8.base64StringToBlocks(base64, n);
            blocksArr = blocks.toArray(new BigInteger[0]);
            total = blocksArr.length;

            System.out.println("[Decrypt][Rank0] Blöcke gesamt: " + total + " | Prozesse: " + size);
        }

        // --- Meta ---
        int[] metaTotal = new int[]{ total };
        comm.Bcast(metaTotal, 0, 1, MPI.INT, 0);
        total = metaTotal[0];

        String[] keyMeta = new String[2];
        if (rank == 0) {
            keyMeta[0] = d.toString();
            keyMeta[1] = n.toString();
        }
        comm.Bcast(keyMeta, 0, 2, MPI.OBJECT, 0);
        d = new BigInteger(keyMeta[0]);
        n = new BigInteger(keyMeta[1]);

        // --- Blöcke broadcasten ---
        if (rank != 0) {
            blocksArr = new BigInteger[total];
        }
        if (total > 0) {
            comm.Bcast(blocksArr, 0, total, MPI.OBJECT, 0);
        }

        // --- Lokale Round-Robin-Entschlüsselung ---
        List<Integer> idx = new ArrayList<>();
        List<BigInteger> vals = new ArrayList<>();
        for (int i = rank; i < total; i += size) {
            BigInteger p = schnelleExponentiation.pow(blocksArr[i], d, n);
            idx.add(i);
            vals.add(p);
        }

        int localCount = idx.size();
        int[] idxArr = new int[localCount];
        for (int i = 0; i < localCount; i++) idxArr[i] = idx.get(i);
        BigInteger[] valArr = vals.toArray(new BigInteger[0]);

        if (rank == 0) {
            BigInteger[] result = new BigInteger[total];

            // eigene
            for (int i = 0; i < localCount; i++) result[idxArr[i]] = valArr[i];

            // andere
            for (int r = 1; r < size; r++) {
                int[] cArr = new int[1];
                comm.Recv(cArr, 0, 1, MPI.INT, r, 200);
                int c = cArr[0];

                int[] ridx = new int[c];
                if (c > 0) comm.Recv(ridx, 0, c, MPI.INT, r, 201);

                BigInteger[] rvals = new BigInteger[c];
                if (c > 0) comm.Recv(rvals, 0, c, MPI.OBJECT, r, 202);

                for (int j = 0; j < c; j++) result[ridx[j]] = rvals[j];
            }

            // Bytes zusammensetzen → UTF-8 String
            List<BigInteger> plainBlocks = Arrays.asList(result);
            int blockSize = RSAUTF8.getEncryptionBlockSize(n);
            byte[] all = RSAUTF8.bigIntegerBlocksToBytes(plainBlocks, blockSize);
            String text = new String(all, StandardCharsets.UTF_8).trim();

            long t1 = System.currentTimeMillis();
            System.out.println("[Decrypt][Rank0] Fertig in " + (t1 - t0) + " ms.");
            System.out.println("\n=== Klartext ===\n" + text);
        } else {
            // an Rank 0 senden
            comm.Send(new int[]{localCount}, 0, 1, MPI.INT, 0, 200);
            if (localCount > 0) {
                comm.Send(idxArr, 0, localCount, MPI.INT, 0, 201);
                comm.Send(valArr, 0, localCount, MPI.OBJECT, 0, 202);
            }
        }

        MPI.Finalize();
    }
}
