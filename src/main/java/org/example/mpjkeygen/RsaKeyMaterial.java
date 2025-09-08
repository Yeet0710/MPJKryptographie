package org.example.mpjkeygen;

import java.math.BigInteger;

/**
 * Container-Klasse für alle RSA-Schlüsselparameter.
 *
 * - Implementiert Serializable → notwendig, damit Objekte über MPI (Bcast, Gather)
 *   verschickt werden können.
 * - Alle Felder sind 'final' → nach der Konstruktion unveränderlich (immutable).
 */
public class RsaKeyMaterial implements java.io.Serializable {

    // Die beiden Primzahlen (geheim, nur für KeyGen/CRT)
    public final BigInteger p;      // erste Primzahl
    public final BigInteger q;      // zweite Primzahl

    // RSA-Modulus und φ(n)
    public final BigInteger n;      // n = p*q (öffentlich)
    public final BigInteger phi;    // φ(n) = (p-1)(q-1), nur für KeyGen nötig

    // Schlüssel-Exponenten
    public final BigInteger e;      // öffentlicher Exponent (meist 65537)
    public final BigInteger d;      // privater Exponent = e^-1 mod φ(n)

    // CRT-Optimierungen für schnelles Entschlüsseln
    public final BigInteger dp;     // d mod (p-1)
    public final BigInteger dq;     // d mod (q-1)
    public final BigInteger qInv;   // q^-1 mod p

    /**
     * Konstruktor: setzt alle Felder.
     * @param p    Primzahl p
     * @param q    Primzahl q
     * @param n    Modulus
     * @param phi  Euler φ(n)
     * @param e    öffentlicher Exponent
     * @param d    privater Exponent
     * @param dp   CRT: d mod (p-1)
     * @param dq   CRT: d mod (q-1)
     * @param qInv CRT: Inverses von q mod p
     */
    public RsaKeyMaterial(BigInteger p, BigInteger q, BigInteger n, BigInteger phi,
                          BigInteger e, BigInteger d, BigInteger dp, BigInteger dq, BigInteger qInv) {
        this.p = p;
        this.q = q;
        this.n = n;
        this.phi = phi;
        this.e = e;
        this.d = d;
        this.dp = dp;
        this.dq = dq;
        this.qInv = qInv;
    }
}
