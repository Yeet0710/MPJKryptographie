package org.example;

import java.math.BigInteger;

public class SchnelleExponentiation {

    /**
     * schnelle Exponentiation mit Modulo
     *  (um den privaten Schlüssel d zu berechnen)
     * @param basis = Basis
     * @param exponent = Exponent
     * @param modulus = Modulo
     * @return  (base^exponent) mod modulus
     */
    public static BigInteger schnelleExponentiation (BigInteger basis, BigInteger exponent, BigInteger modulus) {
        if(modulus.equals(BigInteger.ONE)) return BigInteger.ZERO;
        if(exponent.equals(BigInteger.ZERO)) return BigInteger.ONE;

        BigInteger result = BigInteger.ONE;
        basis = basis.mod(modulus);

        while (exponent.compareTo(BigInteger.ZERO) > 0) {
            // falls das aktuelle Exponent-Bit gesetzt ist (ungerade Zahl)
            if(exponent.mod(BigInteger.TWO).equals(BigInteger.ONE)) {
                result = result.multiply(basis).mod(modulus);
            }
            // shift right
            exponent = exponent.divide(BigInteger.TWO);
            // Basis quadriren und wieder Modulo rechnen
            basis = basis.multiply(basis).mod(modulus);
        }
        return result;
    }

}
