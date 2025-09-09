package org.example.mpjbench;

import mpi.Intracomm;
import mpi.MPI;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.example.RSABenchmark.ParallelRSA;
import org.example.rsa.RSAUTF8;
import org.example.rsa.RSAUtils;

/**
 * Benchmark that parallelises RSA encryption and decryption by
 * distributing plaintext blocks across MPI ranks. The benchmark can
 * be executed with different numbers of processes ("-np" parameter of
 * mpjrun) to observe scaling.
 *
 * <p>The default message is a sample German text, but any message can
 * be substituted easily. Rank 0 measures the runtime of encrypting and
 * decrypting all blocks and reports the average time over a number of
 * repetitions.</p>
 */
public final class ParallelRSABenchMPI {

    // ensure UTF-8 output for logs with umlauts
    static {
        try {
            System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err)), true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private ParallelRSABenchMPI() {}

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();

        // allow --reps <n>
        int reps = 5;
        for (int i = 0; i < args.length; i++) {
            if ("--reps".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                reps = Integer.parseInt(args[++i]);
            }
        }

        String message = "In einer weit, weit entfernten Galaxis entfaltet sich eine epische Saga. Die Geschichte beginnt mit der tyrannischen Herrschaft des Imperiums.";
        BigInteger e = null, d = null, n = null;
        BigInteger[] plainBlocks = null;

        if (rank == 0) {
            RSAUtils.loadKeysFromFiles();
            e = RSAUtils.getBobPublicKey();
            d = RSAUtils.getBobPrivateKey();
            n = RSAUtils.getBobModulus();
            List<BigInteger> blocks = RSAUTF8.textToBigIntegerBlocks(message, n);
            plainBlocks = blocks.toArray(new BigInteger[0]);
            System.out.println("Prozesse: " + comm.Size() + " | Blöcke: " + plainBlocks.length);
        }

        double encSum = 0.0, decSum = 0.0;
        for (int r = 0; r < reps; r++) {
            long t0 = System.currentTimeMillis();
            BigInteger[] cipher = ParallelRSA.encrypt(plainBlocks, e, n, comm);
            long t1 = System.currentTimeMillis();
            ParallelRSA.decrypt(rank == 0 ? cipher : null, d, n, comm);
            long t2 = System.currentTimeMillis();
            if (rank == 0) {
                encSum += (t1 - t0);
                decSum += (t2 - t1);
            }
            comm.Barrier();
        }

        if (rank == 0) {
            System.out.printf(Locale.ROOT, "Durchschnittliche Verschlüsselungszeit: %.3f ms%n", encSum / reps);
            System.out.printf(Locale.ROOT, "Durchschnittliche Entschlüsselungszeit: %.3f ms%n", decSum / reps);
        }

        MPI.Finalize();
    }
}

