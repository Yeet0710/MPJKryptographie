package org.example.mpjkeygen;

import java.math.BigInteger;
/**
 * Lehr-Implementierung: Schnelle Exponentiation (Square-and-Multiply).
 * sonst BigInteger.modPow schneller und sicherer.
 */
public class schnelleExponentiation {

    public static BigInteger pow(BigInteger basis, BigInteger exponent, BigInteger modulus) {
        if (modulus.equals(BigInteger.ONE)) return BigInteger.ZERO;
        if (exponent.equals(BigInteger.ZERO)) return BigInteger.ONE;

        BigInteger result = BigInteger.ONE;
        basis = basis.mod(modulus);

        while (exponent.compareTo(BigInteger.ZERO) > 0) {
            if (exponent.testBit(0)) { // letztes Bit = 1
                result = result.multiply(basis).mod(modulus);
            }
            exponent = exponent.shiftRight(1);   // >> 1
            basis = basis.multiply(basis).mod(modulus);
        }
        return result;
    }

    public static void main(String[] args) {
        BigInteger base = new BigInteger("123456789");
        BigInteger exp  = new BigInteger("987654321");
        BigInteger mod  = new BigInteger("1000000007");

        System.out.println(pow(base, exp, mod));
        // Vergleich mit Java:
        System.out.println(base.modPow(exp, mod));
    }
}
