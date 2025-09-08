package org.example.rsa;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RSAUtils {

    private static BigInteger aliceN;
    private static BigInteger aliceE;
    private static BigInteger aliceD;
    private static BigInteger aliceP;
    private static BigInteger aliceQ;
    private static BigInteger aliceDP;
    private static BigInteger aliceDQ;
    private static BigInteger aliceQInv;

    private static BigInteger bobN;
    private static BigInteger bobE;
    private static BigInteger bobD; // optional – nur vorhanden, falls du es schreibst

    private static boolean loaded = false;

    private RSAUtils() {}

    public static synchronized void loadKeysFromFiles() throws IOException {
        if (loaded) return;

        // ---- Alice (vollständig, wie von MainKeyGen geschrieben) ----
        aliceN   = readBig("alice_n.txt");
        aliceE   = readBig("alice_e.txt");
        aliceD   = readBig("alice_d.txt");
        aliceP   = readBig("alice_p.txt");
        aliceQ   = readBig("alice_q.txt");
        aliceDP  = readBig("alice_dp.txt");
        aliceDQ  = readBig("alice_dq.txt");
        aliceQInv= readBig("alice_qInv.txt");

        // ---- Bob (in deinem aktuellen MainKeyGen nur public) ----
        bobN = readBig("bob_n.txt");
        bobE = readBig("bob_e.txt");

        // bob_d.txt ist aktuell NICHT geschrieben – versuche optional zu laden
        bobD = tryReadBig("bob_d.txt"); // kann null sein

        loaded = true;
    }

    // ----------------- Getter Alice -----------------
    public static BigInteger getAliceModulus()     { ensureLoaded(); return aliceN; }
    public static BigInteger getAlicePublicKey()   { ensureLoaded(); return aliceE; }
    public static BigInteger getAlicePrivateKey()  { ensureLoaded(); return aliceD; }
    public static BigInteger getAliceP()           { ensureLoaded(); return aliceP; }
    public static BigInteger getAliceQ()           { ensureLoaded(); return aliceQ; }
    public static BigInteger getAliceDP()          { ensureLoaded(); return aliceDP; }
    public static BigInteger getAliceDQ()          { ensureLoaded(); return aliceDQ; }
    public static BigInteger getAliceQInv()        { ensureLoaded(); return aliceQInv; }

    // ----------------- Getter Bob -------------------
    public static BigInteger getBobModulus()       { ensureLoaded(); return bobN; }
    public static BigInteger getBobPublicKey()     { ensureLoaded(); return bobE; }

    /** Wirft Exception, wenn bob_d.txt nicht existiert. */
    public static BigInteger getBobPrivateKey() {
        ensureLoaded();
        if (bobD == null) {
            throw new IllegalStateException(
                    "Bob hat aktuell keinen privaten Schlüssel (bob_d.txt fehlt). "
                            + "Passe MainKeyGen an, um auch bob_d.txt zu schreiben, "
                            + "oder entschlüssele mit Alices privatem Schlüssel."
            );
        }
        return bobD;
    }

    // ----------------- Hilfsfunktionen -----------------
    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("RSAUtils.loadKeysFromFiles() zuerst aufrufen.");
        }
    }

    private static BigInteger readBig(String file) throws IOException {
        String s = Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
        return new BigInteger(s);
    }

    private static BigInteger tryReadBig(String file) {
        try {
            if (Files.exists(Path.of(file))) {
                String s = Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) return new BigInteger(s);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Wandelt ein BigInteger sicher in einen String um.
     * - Gibt bei null einen leeren String zurück (damit Broadcast nicht crasht).
     * - Verwendet Basis 10 (dezimal), damit new BigInteger(str) auf allen Ranks funktioniert.
     */
    public static String bigIntegerToStringSafe(BigInteger val) {
        return (val == null) ? "" : val.toString(10);
    }

    /**
     * Wandelt einen String zurück in ein BigInteger.
     * - Gibt null zurück, wenn der String leer oder null ist.
     */
    public static BigInteger stringToBigIntegerSafe(String s) {
        if (s == null || s.isEmpty()) return null;
        return new BigInteger(s, 10);
    }
}