package org.example.RSABenchmark;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;

/**
 * RSA helper that parallelises the modular exponentiation of each block
 * across all MPI ranks.  Rank 0 provides the block data and collects the
 * results, while every process participates in the exponentiation via
 * {@link ParallelSchnelleExponentiation}.
 */
public final class ParallelRSA {

    private ParallelRSA() {}

    /**
     * Encrypt blocks with public exponent {@code e} and modulus {@code n}.
     * The returned array is only valid on rank 0; other ranks return
     * {@code null} but still participate in the computation.
     */
    public static BigInteger[] encrypt(BigInteger[] blocks, BigInteger e, BigInteger n, Intracomm comm) {
        int rank = comm.Rank();
        int total = (rank == 0 && blocks != null) ? blocks.length : 0;

        // Broadcast number of blocks and key parameters
        int[] meta = new int[]{ total };
        comm.Bcast(meta, 0, 1, MPI.INT, 0);
        total = meta[0];

        String[] keyMeta = new String[2];
        if (rank == 0) {
            keyMeta[0] = e.toString();
            keyMeta[1] = n.toString();
        }
        comm.Bcast(keyMeta, 0, 2, MPI.OBJECT, 0);
        e = new BigInteger(keyMeta[0]);
        n = new BigInteger(keyMeta[1]);

        BigInteger[] result = null;
        if (rank == 0 && total > 0) {
            result = new BigInteger[total];
        }

        // Process blocks one after another; each exponentiation is parallel
        for (int i = 0; i < total; i++) {
            String[] blockMeta = new String[1];
            if (rank == 0) {
                blockMeta[0] = blocks[i].toString();
            }
            comm.Bcast(blockMeta, 0, 1, MPI.OBJECT, 0);
            BigInteger block = new BigInteger(blockMeta[0]);

            BigInteger enc = ParallelSchnelleExponentiation.pow(block, e, n, comm);
            if (rank == 0) {
                result[i] = enc;
            }
        }
        return result;
    }

    /**
     * Decrypt blocks with private exponent {@code d} and modulus {@code n}.
     * Only rank 0 receives the resulting plaintext blocks.
     */
    public static BigInteger[] decrypt(BigInteger[] blocks, BigInteger d, BigInteger n, Intracomm comm) {
        int rank = comm.Rank();
        int total = (rank == 0 && blocks != null) ? blocks.length : 0;

        int[] meta = new int[]{ total };
        comm.Bcast(meta, 0, 1, MPI.INT, 0);
        total = meta[0];

        String[] keyMeta = new String[2];
        if (rank == 0) {
            keyMeta[0] = d.toString();
            keyMeta[1] = n.toString();
        }
        comm.Bcast(keyMeta, 0, 2, MPI.OBJECT, 0);
        d = new BigInteger(keyMeta[0]);
        n = new BigInteger(keyMeta[1]);

        BigInteger[] result = null;
        if (rank == 0 && total > 0) {
            result = new BigInteger[total];
        }

        for (int i = 0; i < total; i++) {
            String[] blockMeta = new String[1];
            if (rank == 0) {
                blockMeta[0] = blocks[i].toString();
            }
            comm.Bcast(blockMeta, 0, 1, MPI.OBJECT, 0);
            BigInteger block = new BigInteger(blockMeta[0]);

            BigInteger dec = ParallelSchnelleExponentiation.pow(block, d, n, comm);
            if (rank == 0) {
                result[i] = dec;
            }
        }
        return result;
    }
}
