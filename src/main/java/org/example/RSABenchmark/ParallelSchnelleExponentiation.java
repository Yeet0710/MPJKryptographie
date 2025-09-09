package org.example.RSABenchmark;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;

/**
 * Parallel variant of the binary modular exponentiation used by RSA.
 * The exponentiation is decomposed into precomputed powers of the base and
 * each process multiplies a subset of these powers.  The partial results are
 * then combined on rank 0 and broadcast back to all ranks.
 */
public final class ParallelSchnelleExponentiation {

    private ParallelSchnelleExponentiation() {}

    public static BigInteger pow(BigInteger base, BigInteger exponent, BigInteger modulus, Intracomm comm) {
        int rank = comm.Rank();
        int size = comm.Size();

        int bitLen = 0;
        BigInteger[] powers = null;
        if (rank == 0) {
            bitLen = exponent.bitLength();
            powers = new BigInteger[bitLen];
            BigInteger cur = base.mod(modulus);
            for (int i = 0; i < bitLen; i++) {
                powers[i] = cur;
                cur = cur.multiply(cur).mod(modulus);
            }
        }

        int[] meta = new int[]{ bitLen };
        comm.Bcast(meta, 0, 1, MPI.INT, 0);
        bitLen = meta[0];

        // Broadcast powers
        if (rank != 0) {
            powers = new BigInteger[bitLen];
        }
        if (bitLen > 0) {
            comm.Bcast(powers, 0, bitLen, MPI.OBJECT, 0);
        }

        // Broadcast exponent
        String[] expMeta = new String[1];
        if (rank == 0) {
            expMeta[0] = exponent.toString();
        }
        comm.Bcast(expMeta, 0, 1, MPI.OBJECT, 0);
        exponent = new BigInteger(expMeta[0]);

        BigInteger local = BigInteger.ONE;
        for (int i = rank; i < bitLen; i += size) {
            if (exponent.testBit(i)) {
                local = local.multiply(powers[i]).mod(modulus);
            }
        }

        BigInteger result;
        if (rank == 0) {
            result = local;
            for (int r = 1; r < size; r++) {
                BigInteger[] recv = new BigInteger[1];
                comm.Recv(recv, 0, 1, MPI.OBJECT, r, 0);
                result = result.multiply(recv[0]).mod(modulus);
            }
        } else {
            comm.Send(new BigInteger[]{ local }, 0, 1, MPI.OBJECT, 0, 0);
            result = BigInteger.ZERO; // placeholder, will be overwritten
        }

        // Broadcast final result so every rank returns the same value
        BigInteger[] resArr;
        if (rank == 0) {
            resArr = new BigInteger[]{ result };
        } else {
            resArr = new BigInteger[1];
        }
        comm.Bcast(resArr, 0, 1, MPI.OBJECT, 0);
        return resArr[0];
    }
}
