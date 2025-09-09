package org.example.RSABenchmark;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;

/**
 * RSA helper that distributes block exponentiations across MPI ranks.
 * Rank 0 scatters block data to all ranks and gathers the results, while each
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

        // Scatter blocks so each rank only receives its portion
        int base = total / size;
        int rem = total % size;
        int[] counts = new int[size];
        int[] displs = new int[size];
        int offset = 0;
        for (int r = 0; r < size; r++) {
            counts[r] = base + (r < rem ? 1 : 0);
            displs[r] = offset;
            offset += counts[r];
        }
        int localCount = counts[rank];

        String[] sendBuf = new String[0];
        if (rank == 0 && blocks != null) {
            sendBuf = new String[total];
            for (int i = 0; i < total; i++) {
                sendBuf[i] = blocks[i].toString();
            }
        }
        String[] recvBuf = new String[localCount];
        if (total > 0) {
            comm.Scatterv(sendBuf, 0, counts, displs, MPI.OBJECT, recvBuf, 0, localCount, MPI.OBJECT, 0);
        }

        // Process received blocks locally
        String[] localVals = new String[localCount];
        for (int i = 0; i < localCount; i++) {
            BigInteger enc = ParallelSchnelleExponentiation.pow(new BigInteger(recvBuf[i]), e, n, (Intracomm) MPI.COMM_SELF);
            localVals[i] = enc.toString();
        }

        BigInteger[] result = null;
        if (rank == 0 && total > 0) {
            String[] gathered = new String[total];

            comm.Gatherv(localVals, 0, localCount, MPI.OBJECT, gathered, 0, counts, displs, MPI.OBJECT, 0);

            result = new BigInteger[total];
            for (int i = 0; i < total; i++) {
                result[i] = new BigInteger(gathered[i]);
            }
        } else if (total > 0) {

            comm.Gatherv(localVals, 0, localCount, MPI.OBJECT, new String[0], 0, counts, displs, MPI.OBJECT, 0);

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

        // Scatter blocks so each rank only receives its portion
        int base = total / size;
        int rem = total % size;
        int[] counts = new int[size];
        int[] displs = new int[size];
        int offset = 0;
        for (int r = 0; r < size; r++) {
            counts[r] = base + (r < rem ? 1 : 0);
            displs[r] = offset;
            offset += counts[r];
        }
        int localCount = counts[rank];

        String[] sendBuf = new String[0];

        if (rank == 0 && blocks != null) {
            sendBuf = new String[total];
            for (int i = 0; i < total; i++) {
                sendBuf[i] = blocks[i].toString();
            }
        }
        String[] recvBuf = new String[localCount];
        if (total > 0) {

            comm.Scatterv(sendBuf, 0, counts, displs, MPI.OBJECT, recvBuf, 0, localCount, MPI.OBJECT, 0);
        }
        // Process received blocks locally
        String[] localVals = new String[localCount];
        for (int i = 0; i < localCount; i++) {
            BigInteger dec = ParallelSchnelleExponentiation.pow(new BigInteger(recvBuf[i]), d, n, (Intracomm) MPI.COMM_SELF);
            localVals[i] = dec.toString();
        }

        BigInteger[] result = null;
        if (rank == 0 && total > 0) {
            String[] gathered = new String[total];

            comm.Gatherv(localVals, 0, localCount, MPI.OBJECT, gathered, 0, counts, displs, MPI.OBJECT, 0);

            result = new BigInteger[total];
            for (int i = 0; i < total; i++) {
                result[i] = new BigInteger(gathered[i]);
            }
        } else if (total > 0) {

            comm.Gatherv(localVals, 0, localCount, MPI.OBJECT, new String[0], 0, counts, displs, MPI.OBJECT, 0);

        }
        return result;
    }
}
