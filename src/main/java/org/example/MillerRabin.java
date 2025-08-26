package org.example;

import java.math.BigInteger;
import java.security.SecureRandom;

public class MillerRabin {

    /**
     * Miller–Rabin-Test: prüft, ob n vermutlich prim ist.
     * Verwendet schnelleExponentiation für a^d mod n.
     *
     * @param n          Ungerade Zahl > 2
     * @param iterations Anzahl der Test-Runden
     * @param rnd        SecureRandom-Instanz
     * @return true, falls n vermutlich prim
     */
    public static boolean isProbablePrimeMR(BigInteger n, int iterations, SecureRandom rnd) {
        Log.processStart("Miller-Rabin (nBits=%d, rounds=%d)", n.bitLength(), iterations);

        if (n.compareTo(BigInteger.TWO) < 0) {
            Log.processEnd("Miller-Rabin (Ergebnis=composite:<2)");
            return false;
        }
        if (n.equals(BigInteger.TWO) || n.equals(BigInteger.valueOf(3))) {
            Log.processEnd("Miller-Rabin (Ergebnis=prime:2|3)");
            return true;
        }
        if (n.mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
            Log.processEnd("Miller-Rabin (Ergebnis=composite:even)");
            return false;
        }

        // schreibe n-1 = 2^s * d mit d ungerade
        BigInteger d = n.subtract(BigInteger.ONE);
        int s = d.getLowestSetBit();
        d = d.shiftRight(s);

        for (int i = 0; i < iterations; i++) {
            // Zufällige Basis a ∈ [2, n-2]
            BigInteger a;
            do {
                a = new BigInteger(n.bitLength(), rnd);
            } while (a.compareTo(BigInteger.TWO) < 0 || a.compareTo(n.subtract(BigInteger.TWO)) > 0);

            BigInteger x = SchnelleExponentiation.schnelleExponentiation(a, d, n);
            if (x.equals(BigInteger.ONE) || x.equals(n.subtract(BigInteger.ONE))) {
                // Runde überstanden
                continue;
            }

            boolean passed = false;
            for (int r = 1; r < s; r++) {
                x = SchnelleExponentiation.schnelleExponentiation(x, BigInteger.TWO, n);
                if (x.equals(n.subtract(BigInteger.ONE))) {
                    passed = true;
                    break;
                }
            }
            if (!passed) {
                Log.processEnd("Miller-Rabin (Ergebnis=composite:fail@round=%d)", i + 1);
                return false;
            }
        }
        Log.processEnd("Miller-Rabin (Ergebnis=probable_prime)");
        return true;
    }
}
