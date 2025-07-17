package org.example;

import java.math.BigInteger;
import java.security.SecureRandom;

public class MillerRabin {

    /**
     * Miller–Rabin-Test: prüft, ob n vermutlich prim ist.
     * Verwendet die schnelleExponentiation für a^d mod n.
     *
     * @param n          Ungerade Zahl > 2
     * @param iterations Anzahl der Test-Runden
     * @param rnd        SecureRandom-Instanz
     * @return true, falls n vermutlich prim
     */
    public static boolean isProbablePrimeMR(BigInteger n, int iterations, SecureRandom rnd) {
        if (n.compareTo(BigInteger.TWO) < 0) return false;
        if (n.equals(BigInteger.TWO) || n.equals(BigInteger.valueOf(3))) return true;
        if (n.mod(BigInteger.TWO).equals(BigInteger.ZERO)) return false;

        // Schreibe n-1 = 2^s * d mit d ungerade
        BigInteger d = n.subtract(BigInteger.ONE);
        // Gibt den Faktor wieder, da die Anzahl der Nullen am Ende gezählt werden
        int s = d.getLowestSetBit();
        // Der Rest ist dann d
        d = d.shiftRight(s);

        for (int i = 0; i < iterations; i++) {
            // Zufällige Basis a ∈ [2, n-2]
            BigInteger a;
            do {
                a = new BigInteger(n.bitLength(), rnd);
            } while (a.compareTo(BigInteger.TWO) < 0 || a.compareTo(n.subtract(BigInteger.TWO)) > 0);

            // Erstes a^d mod n
            BigInteger x = SchnelleExponentiation.schnelleExponentiation(a, d, n);
            if (x.equals(BigInteger.ONE) || x.equals(n.subtract(BigInteger.ONE))) {
                continue;
            }
            boolean passed = false;
            for (int r = 1; r < s; r++) {
                // Quadrieren: x = x^2 mod n
                x = SchnelleExponentiation.schnelleExponentiation(x, BigInteger.TWO, n);
                if (x.equals(n.subtract(BigInteger.ONE))) {
                    passed = true;
                    break;
                }
            }
            if (!passed) {
                return false; // n ist zusammengesetzt
            }
        }
        return true; // vermutlich prim
    }

}
