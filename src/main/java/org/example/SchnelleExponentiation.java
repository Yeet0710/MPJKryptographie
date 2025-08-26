package org.example;

import java.math.BigInteger;

public class SchnelleExponentiation {

    /**
     * Schnelle Exponentiation mit Modulo.
     * @return (basis^exponent) mod modulus
     */
    public static BigInteger schnelleExponentiation(BigInteger basis, BigInteger exponent, BigInteger modulus) {
        Log.processStart("FastExp (baseBits=%d, expBits=%d, modBits=%d)",
                basis.bitLength(), exponent.bitLength(), modulus.bitLength());

        if (modulus.equals(BigInteger.ONE)) {
            Log.processEnd("FastExp (Ergebnis=0:mod=1)");
            return BigInteger.ZERO;
        }
        if (exponent.equals(BigInteger.ZERO)) {
            Log.processEnd("FastExp (Ergebnis=1:exp=0)");
            return BigInteger.ONE;
        }

        BigInteger result = BigInteger.ONE;
        basis = basis.mod(modulus);

        while (exponent.compareTo(BigInteger.ZERO) > 0) {
            if (exponent.testBit(0)) {
                result = result.multiply(basis).mod(modulus);
            }
            exponent = exponent.shiftRight(1);
            basis = basis.multiply(basis).mod(modulus);
        }

        Log.processEnd("FastExp (resBits=%d)", result.bitLength());
        return result;
    }
}
