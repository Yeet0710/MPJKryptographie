package org.example.RSABenchmark;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;

/**
 * RSA helper that distributes block exponentiations across MPI ranks.
 * Rank 0 broadcasts all block data and collects the results, while each
 * process performs its assigned exponentiations using
 * {@link ParallelSchnelleExponentiation} locally.
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
        int size = comm.Size();
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

        // Broadcast all blocks once
        String[] blockMeta = new String[total];
        if (rank == 0 && blocks != null) {
            for (int i = 0; i < total; i++) {
                blockMeta[i] = blocks[i].toString();
            }
        }
        if (total > 0) {
            comm.Bcast(blockMeta, 0, total, MPI.OBJECT, 0);
        }
        BigInteger[] allBlocks = new BigInteger[total];
        for (int i = 0; i < total; i++) {
            allBlocks[i] = new BigInteger(blockMeta[i]);
        }

        // Each rank processes its own block indices
        int count = 0;
        for (int i = rank; i < total; i += size) {
            count++;
        }
        int[] indices = new int[count];
        String[] values = new String[count];
        int c = 0;
        for (int i = rank; i < total; i += size) {
            BigInteger enc = ParallelSchnelleExponentiation.pow(allBlocks[i], e, n, (Intracomm) MPI.COMM_SELF);
            indices[c] = i;
            values[c] = enc.toString();
            c++;
        }

        BigInteger[] result = null;
        if (rank == 0 && total > 0) {
            result = new BigInteger[total];
            for (int j = 0; j < count; j++) {
                result[indices[j]] = new BigInteger(values[j]);
            }
            for (int r = 1; r < size; r++) {
                int rc = 0;
                for (int i = r; i < total; i += size) {
                    rc++;
                }
                int[] rIdx = new int[rc];
                String[] rVal = new String[rc];
                comm.Recv(rIdx, 0, rc, MPI.INT, r, 0);
                comm.Recv(rVal, 0, rc, MPI.OBJECT, r, 1);
                for (int j = 0; j < rc; j++) {
                    result[rIdx[j]] = new BigInteger(rVal[j]);
                }
            }
        } else {
            comm.Send(indices, 0, count, MPI.INT, 0, 0);
            comm.Send(values, 0, count, MPI.OBJECT, 0, 1);
        }
        return result;
    }

    /**
     * Decrypt blocks with private exponent {@code d} and modulus {@code n}.
     * Only rank 0 receives the resulting plaintext blocks.
     */
    public static BigInteger[] decrypt(BigInteger[] blocks, BigInteger d, BigInteger n, Intracomm comm) {
        int rank = comm.Rank();
        int size = comm.Size();
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

        String[] blockMeta = new String[total];
        if (rank == 0 && blocks != null) {
            for (int i = 0; i < total; i++) {
                blockMeta[i] = blocks[i].toString();
            }
        }
        if (total > 0) {
            comm.Bcast(blockMeta, 0, total, MPI.OBJECT, 0);
        }
        BigInteger[] allBlocks = new BigInteger[total];
        for (int i = 0; i < total; i++) {
            allBlocks[i] = new BigInteger(blockMeta[i]);
        }

        int count = 0;
        for (int i = rank; i < total; i += size) {
            count++;
        }
        int[] indices = new int[count];
        String[] values = new String[count];
        int c = 0;
        for (int i = rank; i < total; i += size) {
            BigInteger dec = ParallelSchnelleExponentiation.pow(allBlocks[i], d, n, (Intracomm) MPI.COMM_SELF);
            indices[c] = i;
            values[c] = dec.toString();
            c++;
        }

        BigInteger[] result = null;
        if (rank == 0 && total > 0) {
            result = new BigInteger[total];
            for (int j = 0; j < count; j++) {
                result[indices[j]] = new BigInteger(values[j]);
            }
            for (int r = 1; r < size; r++) {
                int rc = 0;
                for (int i = r; i < total; i += size) {
                    rc++;
                }
                int[] rIdx = new int[rc];
                String[] rVal = new String[rc];
                comm.Recv(rIdx, 0, rc, MPI.INT, r, 0);
                comm.Recv(rVal, 0, rc, MPI.OBJECT, r, 1);
                for (int j = 0; j < rc; j++) {
                    result[rIdx[j]] = new BigInteger(rVal[j]);
                }
            }
        } else {
            comm.Send(indices, 0, count, MPI.INT, 0, 0);
            comm.Send(values, 0, count, MPI.OBJECT, 0, 1);
        }
        return result;
    }
}
