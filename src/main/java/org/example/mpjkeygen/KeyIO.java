package org.example.mpjkeygen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// atomar schreiben/lesen (nur Rank 0 schreibt)
/**
 * Utility-Klasse für atomisches Schreiben von Schlüsseldateien.
 *
 * - Verhindert halb-geschriebene Dateien (z. B. bei Crash oder Parallelzugriff).
 * - Vorgehen:
 *   1. Inhalt in temporäre Datei path.tmp schreiben.
 *   2. Temp-Datei atomar auf Ziel-Datei verschieben (rename).
 */
public final class KeyIO {

    private KeyIO() {} // Utility-Klasse → kein Konstruktor erlaubt

    /**
     * Schreibt Text in Datei 'path' atomar.
     * @param path    Zieldateiname (z. B. "rsa_n.txt")
     * @param content Inhalt als String
     */
    public static void writeAtomically(String path, String content) throws IOException {
        Path target = Paths.get(path);        // Ziel-Datei
        Path temp   = Paths.get(path + ".tmp"); // temporäre Datei

        // 1. In Temp-Datei schreiben (erstellt oder überschreibt sie)
        Files.writeString(temp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        // 2. Temp-Datei atomar verschieben → ersetzt Ziel falls schon da
        Files.move(temp, target,
                StandardCopyOption.ATOMIC_MOVE,       // garantiert: entweder ganz oder gar nicht
                StandardCopyOption.REPLACE_EXISTING); // vorhandene Datei wird ersetzt
    }
}

