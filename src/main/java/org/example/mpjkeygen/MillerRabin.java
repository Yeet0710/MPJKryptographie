package org.example.mpjkeygen;

import org.example.SchnelleExponentiation;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Miller–Rabin Primzahltest ohne externe Logging-Abhängigkeiten.
 * - Fast-Exits für kleine n
 * - kleiner Primteiler-Check, um viele Kandidaten schnell zu verwerfen
 * - saubere, gleichverteilte Basiswahl im Bereich [2, n-2]
 * - Overload mit sinnvollen Standard-Runden nach Bitlänge
 */
public final class MillerRabin {

    private MillerRabin() {}

    /** Bequemer Wrapper: wählt Runden abhängig von der Bitlänge. */
    public static boolean isProbablePrime(BigInteger n, SecureRandom rnd) {
        return isProbablePrimeMR(n, defaultRounds(n.bitLength()), rnd);
    }

    /**
     * Miller–Rabin-Test: prüft, ob n vermutlich prim ist.
     * Verwendet SchnelleExponentiation für a^d mod n.
     *
     * @param n          Ungerade Zahl > 2 (gerade Zahlen werden sofort verworfen)
     * @param iterations Anzahl der Test-Runden (>=1 empfohlen)
     * @param rnd        SecureRandom-Instanz (falls null, wird intern erzeugt)
     * @return true, falls n vermutlich prim
     */
    public static boolean isProbablePrimeMR(BigInteger n, int iterations, SecureRandom rnd) {
        Objects.requireNonNull(n, "n");
        if (iterations <= 0) iterations = 1;
        if (rnd == null) rnd = new SecureRandom();

        // Triviale Fälle
        if (n.compareTo(BigInteger.TWO) < 0) return false;              // n < 2
        if (n.equals(BigInteger.TWO) || n.equals(BigInteger.valueOf(3))) return true;
        if (n.testBit(0) == false) return false;                         // gerade

        // schneller Primteiler-Check für kleine Primes (vermeidet teure MR-Runden bei klaren Kompositen)
        final int[] smallPrimes = {3,5,7,11,13,17,19,23,29,31,37};
        for (int p : smallPrimes) {
            BigInteger P = BigInteger.valueOf(p);
            if (n.equals(P)) return true;
            if (n.mod(P).equals(BigInteger.ZERO)) return false;
        }

        // schreibe n-1 = 2^s * d mit d ungerade
        BigInteger d = n.subtract(BigInteger.ONE);
        int s = d.getLowestSetBit();   // Anzahl Zweierfaktoren
        d = d.shiftRight(s);

        final BigInteger nMinusOne = n.subtract(BigInteger.ONE);
        final BigInteger two = BigInteger.TWO;

        for (int i = 0; i < iterations; i++) {
            // Wähle zufällige Basis a ∈ [2, n-2] gleichverteilt
            BigInteger a = randomInRange(two, nMinusOne, rnd);

            // x = a^d mod n
            BigInteger x = SchnelleExponentiation.schnelleExponentiation(a, d, n);
            if (x.equals(BigInteger.ONE) || x.equals(nMinusOne)) {
                continue; // Runde überstanden
            }

            boolean passed = false;
            for (int r = 1; r < s; r++) {
                // x = x^2 mod n
                x = SchnelleExponentiation.schnelleExponentiation(x, two, n);
                if (x.equals(nMinusOne)) {
                    passed = true;
                    break;
                }
            }
            if (!passed) {
                return false; // sicher zusammengesetzt
            }
        }
        return true; // wahrscheinlich prim
    }

    /** Wählt eine gleichverteilte Zufallszahl im (inklusive) Bereich [lo, hi], vorausgesetzt lo <= hi. */
    private static BigInteger randomInRange(BigInteger lo, BigInteger hi, SecureRandom rnd) {
        BigInteger range = hi.subtract(lo);           // >= 0
        int bitLength = range.bitLength();

        // Rejection Sampling: ziehe 0..range so lange, bis <= range
        BigInteger r;
        do {
            r = new BigInteger(bitLength + 1, rnd);   // +1, um Bias bei 2^k-1 zu vermeiden
        } while (r.compareTo(range) > 0);
        return lo.add(r);
    }

    /** Heuristische Standardrunden nach Bitlänge (Fehlerwahrscheinlichkeit <= 2^-80 i.d.R.). */
    private static int defaultRounds(int nBits) {
        if (nBits < 64)   return 8;
        if (nBits < 128)  return 6;
        if (nBits < 256)  return 5;
        if (nBits < 512)  return 7;   // häufig viele Kandidaten → etwas mehr Runden
        if (nBits < 1024) return 5;
        if (nBits < 2048) return 4;
        return 3;                      // 2048+ Bit: 3–4 Runden reichen typischerweise
    }
}
