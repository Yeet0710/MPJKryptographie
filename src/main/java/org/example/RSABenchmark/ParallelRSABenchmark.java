package org.example.RSABenchmark;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.example.rsa.RSAUTF8;
import org.example.rsa.RSAUtils;

/**
 * Benchmark that runs a single RSA encryption/decryption where the
 * modular exponentiation is parallelised across MPI processes.
 * Rank 0 prepares the data and measures the timings.
 */
public class ParallelRSABenchmark {

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();

        String message = "M\u00f6ge die Macht mit dir sein!";
        BigInteger e = null, d = null, n = null;
        BigInteger[] plainBlocks = null;

        if (rank == 0) {
            RSAUtils.loadKeysFromFiles();
            e = RSAUtils.getBobPublicKey();
            d = RSAUtils.getBobPrivateKey();
            n = RSAUtils.getBobModulus();
            List<BigInteger> blocks = RSAUTF8.textToBigIntegerBlocks(message, n);
            plainBlocks = blocks.toArray(new BigInteger[0]);
            System.out.println("[Benchmark][Rank0] Bl\u00f6cke: " + plainBlocks.length + " | Prozesse: " + comm.Size());
        }

        long t0 = System.currentTimeMillis();
        BigInteger[] cipherBlocks = ParallelRSA.encrypt(plainBlocks, e, n, comm);
        long t1 = System.currentTimeMillis();
        BigInteger[] decrypted = ParallelRSA.decrypt(cipherBlocks, d, n, comm);
        long t2 = System.currentTimeMillis();

        if (rank == 0) {
            int blockSize = RSAUTF8.getEncryptionBlockSize(n);
            byte[] bytes = RSAUTF8.bigIntegerBlocksToBytes(List.of(decrypted), blockSize);
            String recovered = new String(bytes, StandardCharsets.UTF_8).trim();
            System.out.println("[Benchmark][Rank0] Verschl\u00fcsselung: " + (t1 - t0) + " ms");
            System.out.println("[Benchmark][Rank0] Entschl\u00fcsselung: " + (t2 - t1) + " ms");
            System.out.println("[Benchmark][Rank0] Zur\u00fcckgewonnener Text: " + recovered);
        }
        MPI.Finalize();
    }
}
