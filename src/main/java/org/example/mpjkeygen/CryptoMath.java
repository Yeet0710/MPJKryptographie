package org.example.mpjkeygen;
import java.math.BigInteger;
// erweiterter Euklid, gcd, modInverse

/**
 * Krypto-Hilfsfunktionen:
 * - ggT (ggt)
 * - Teilerfremdheit (sindTeilerfremd)
 * - Erweiterter Euklid (erweiterterEuklid) -> {g, x, y} mit ax + by = g
 * - Modularer Inverser (modInverse)
 *
 * Hinweis:
 *  - Alle Methoden sind stateless und threadsafe.
 *  - modInverse wirft eine ArithmeticException, falls kein Inverses existiert.
 */
public class CryptoMath {

    // ---------------------------
    // Größter gemeinsamer Teiler (iterativ)
    // ---------------------------
    public static BigInteger ggt(BigInteger a, BigInteger b) {
        // Normalisieren (optional): negative Werte auf positives Äquivalent abbilden
        a = a.abs();
        b = b.abs();
        while (!b.equals(BigInteger.ZERO)) {
            BigInteger tmp = b;
            b = a.mod(b);
            a = tmp;
        }
        return a;
    }

    // ---------------------------
    // Teilerfremd?
    // ---------------------------
    public static boolean sindTeilerfremd(BigInteger a, BigInteger b) {
        return ggt(a, b).equals(BigInteger.ONE);
    }

    // ---------------------------
    // Erweiterter Euklid (rekursiv)
    //    Rückgabe: { g, x, y } mit g = ggT(a,b) und a*x + b*y = g
    // ---------------------------
    public static BigInteger[] erweiterterEuklid(BigInteger a, BigInteger b) {
        if (b.equals(BigInteger.ZERO)) {
            // ggT = a, x = 1, y = 0
            return new BigInteger[]{ a, BigInteger.ONE, BigInteger.ZERO };
        }
        BigInteger[] r = erweiterterEuklid(b, a.mod(b));
        BigInteger g  = r[0];
        BigInteger x1 = r[1];
        BigInteger y1 = r[2];

        BigInteger x = y1;
        BigInteger y = x1.subtract(a.divide(b).multiply(y1));
        return new BigInteger[]{ g, x, y };
    }

    // ---------------------------
    // Modularer Inverser: a^{-1} mod m
    //    Wirft ArithmeticException, falls ggT(a,m) != 1.
    // ---------------------------
    public static BigInteger modInverse(BigInteger a, BigInteger m) {
        BigInteger[] r = erweiterterEuklid(a, m);
        BigInteger g = r[0];
        if (!g.equals(BigInteger.ONE)) {
            throw new ArithmeticException("Kein modularer Inverser: gcd(a, m) = " + g);
        }
        // x kann negativ sein -> auf [0, m-1] normalisieren
        BigInteger x = r[1];
        return x.mod(m);
    }


    // ---------------------------
    // Selbsttest
    // ---------------------------
    public static void main(String[] args) {
        BigInteger a = new BigInteger("120");
        BigInteger b = new BigInteger("23");

        BigInteger g = ggt(a, b);
        System.out.println("ggT(120,23) = " + g);

        BigInteger[] r = erweiterterEuklid(a, b);
        System.out.println("g = " + r[0] + ", x = " + r[1] + ", y = " + r[2]);
        // Prüfen: a*x + b*y == g
        System.out.println("Check: " + a.multiply(r[1]).add(b.multiply(r[2])));

        System.out.println("Teilerfremd? " + sindTeilerfremd(a, b));

        // Beispiel modInverse
        BigInteger m = new BigInteger("101"); // prim
        BigInteger inv = modInverse(b, m);
        System.out.println("23^{-1} mod 101 = " + inv);
        System.out.println("(23 * inv) mod 101 = " + b.multiply(inv).mod(m));
    }

}
