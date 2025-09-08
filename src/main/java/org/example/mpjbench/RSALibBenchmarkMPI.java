package org.example.mpjbench;

import mpi.Intracomm;
import mpi.MPI;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import org.example.mpjkeygen.schnelleExponentiation;
import org.example.rsa.RSAUTF8;
import org.example.rsa.RSAUtils;

public class RSALibBenchmarkMPI {

    // Erzwinge saubere UTF-8-Ausgabe (Umlaute in Texten)
    static {
        try {
            System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err)), true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private static final class Stats {
        double sum = 0, sumSq = 0;
        long n = 0;
        void add(double v){ sum += v; sumSq += v*v; n++; }
        double mean(){ return sum / Math.max(1, n); }
        double sd(){ double m = mean(); return Math.sqrt(Math.max(0, sumSq / Math.max(1, n) - m*m)); }
    }

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();
        int size = comm.Size();

        // ---------------- CLI ----------------
        // Richtung der Kommunikation:
        //  - alice2bob: verschlüssele mit Bob.e und entschlüssele mit Bob.d
        //  - bob2alice: verschlüssele mit Alice.e und entschlüssele mit Alice.d
        String who = "alice2bob";
        int[] repsList = new int[]{100, 250, 500};

        for (int i = 0; i < args.length; i++) {
            if ("--who".equalsIgnoreCase(args[i]) && i+1 < args.length) {
                who = args[++i].trim().toLowerCase(Locale.ROOT);
            } else if ("--reps".equalsIgnoreCase(args[i]) && i+1 < args.length) {
                repsList = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray();
            }
        }

        // ---------------- Setup (Rank 0) ----------------
        String[] texts = new String[0];
        BigInteger pubE = null, privD = null, modN = null;
        String directionLabel = "";

        if (rank == 0) {
            RSAUtils.loadKeysFromFiles(); // erwartet deine vorhandenen alice_*/bob_* Dateien

            switch (who) {
                case "alice2bob":
                    pubE  = RSAUtils.getBobPublicKey();
                    privD = RSAUtils.getBobPrivateKey();
                    modN  = RSAUtils.getBobModulus();
                    directionLabel = "Alice to Bob (Bobs e/d)";
                    break;
                case "bob2alice":
                    pubE  = RSAUtils.getAlicePublicKey();
                    privD = RSAUtils.getAlicePrivateKey();
                    modN  = RSAUtils.getAliceModulus();
                    directionLabel = "Bob to Alice (Alices e/d)";
                    break;
                default:
                    System.err.println("Unbekanntes --who: " + who + " (erlaubt: alice2bob | bob2alice)");
                    MPI.Finalize();
                    return;
            }

            // Beispieltexte (du kannst hier einfach erweitern/ändern)
            texts = new String[]{
                    "Möge die Macht mit dir sein!",
                    "Luke Skywalker entdeckt, dass er Teil einer uralten Machttradition ist. Als er sich der Rebellenallianz anschließt, kämpft er gegen das galaktische Imperium, das die Galaxis unterdrückt. Mit Hilfe von Obi-Wan Kenobi, Prinzessin Leia und Han Solo begibt er sich auf eine gefährliche Mission, um den Todesstern zu zerstören und die Freiheit in der Galaxis wiederherzustellen.",
                    "In einer weit, weit entfernten Galaxis entfaltet sich eine epische Saga. Die Geschichte beginnt mit der tyrannischen Herrschaft des Imperiums, das unter der Führung von Darth Vader und dem finsteren Imperator über unzählige Welten herrscht. In dieser düsteren Zeit lebt Luke Skywalker, ein junger Farmer auf dem Wüstenplaneten Tatooine, der von einer geheimnisvollen Macht angezogen wird. Als er den alten Jedi-Meister Obi-Wan Kenobi trifft, erfährt er von seinem wahren Erbe und der Macht, die in ihm schlummert. Luke schließt sich einer kleinen, aber entschlossenen Rebellenallianz an, angeführt von Prinzessin Leia, deren Tapferkeit und Visionen von Freiheit die Hoffnung in den Herzen der Unterdrückten neu entfachen. Gemeinsam mit dem charmanten Schmuggler Han Solo und seinem treuen Co-Piloten Chewbacca begibt sich Luke auf eine gefährliche Mission: den Todesstern, eine gewaltige Raumstation mit der Fähigkeit, ganze Planeten zu vernichten, zu zerstören. Während die Rebellen hinter geheimen Plänen her sind, die die Schwachstelle des Todessterns enthüllen, entbrennt ein Kampf zwischen Licht und Dunkelheit. Darth Vader, einst ein vielversprechender Jedi, wurde von der dunklen Seite der Macht verführt und dient nun dem Imperium. In epischen Duellen, in denen Lichtschwerter aufeinanderprallen, wird der Glaube an die Macht auf die Probe gestellt. Im Laufe der Geschichte wachsen die Helden über sich hinaus und lernen, dass der Schlüssel zur Freiheit nicht nur in der Macht liegt, sondern auch im Mut, die Wahrheit zu suchen und für das Richtige zu kämpfen. Die Rebellion stellt sich den scheinbar unbezwingbaren Kräften des Imperiums entgegen und zeigt, dass selbst in den dunkelsten Zeiten das Licht der Hoffnung niemals erlischt. Diese Geschichte ist mehr als ein Kampf zwischen Gut und Böse – sie ist ein Zeugnis von Loyalität, Freundschaft und der unerschütterlichen Überzeugung, dass jeder Einzelne das Schicksal der Galaxis verändern kann."
            };

            System.out.println("Prozesse: " + size + " | Richtung: " + directionLabel +
                    " | n-Bit: " + modN.bitLength() + "\n");
        }

        // ---- Broadcast Meta + Keys ----
        int[] tCount = new int[]{ (rank==0) ? texts.length : 0 };
        comm.Bcast(tCount, 0, 1, MPI.INT, 0);
        int T = tCount[0];

        if (rank != 0) texts = new String[T];
        comm.Bcast(texts, 0, T, MPI.OBJECT, 0);

        String[] keyStr = new String[3];
        if (rank == 0) {
            keyStr[0] = RSAUtils.bigIntegerToStringSafe(modN);
            keyStr[1] = RSAUtils.bigIntegerToStringSafe(pubE);
            keyStr[2] = RSAUtils.bigIntegerToStringSafe(privD);
        }
        comm.Bcast(keyStr, 0, 3, MPI.OBJECT, 0);

        if (rank != 0) {
            modN  = new BigInteger(keyStr[0]);
            pubE  = new BigInteger(keyStr[1]);
            privD = new BigInteger(keyStr[2]);
        }

        // -------- Warmup (JIT anwerfen) --------
        if (T > 0) {
            var warmBlocks = RSAUTF8.textToBigIntegerBlocks(texts[0], modN);
            if (!warmBlocks.isEmpty()) {
                BigInteger m = warmBlocks.get(0);
                schnelleExponentiation.pow(m, pubE, modN);
                schnelleExponentiation.pow(m, privD, modN);
            }
        }
        comm.Barrier();

        // ---------------- Benchmark: pro Text eigenständige Ausgabe ----------------
        for (int ti = 0; ti < T; ti++) {

            // Header wie im Screenshot (Rank 0 berechnet Länge und Blockanzahl)
            int textLen = 0;
            int blockCount = 0;
            if (rank == 0) {
                textLen = texts[ti].getBytes(StandardCharsets.UTF_8).length;
                blockCount = RSAUTF8.textToBigIntegerBlocks(texts[ti], modN).size();
                System.out.println("== Text " + (ti + 1) + " | Länge: " + textLen + " Byte | Blöcke: " + blockCount + " ==");
            }

            for (int reps : repsList) {
                // faire Verteilung auf Ranks
                int base = reps / size, rest = reps % size;
                int localReps = base + ((rank < rest) ? 1 : 0);

                // Lokale Statistik nur für diesen Text
                Stats enc = new Stats();
                Stats dec = new Stats();

                for (int r = 0; r < localReps; r++) {
                    // Blöcke für diesen Text erzeugen (gehört nicht in die Messwerte – wie in deinem Screenshot)
                    var blocks = RSAUTF8.textToBigIntegerBlocks(texts[ti], modN);

                    // Encrypt (alle Blöcke)
                    long t0 = System.nanoTime();
                    var encBlocks = new ArrayList<BigInteger>(blocks.size());
                    for (BigInteger b : blocks) {
                        encBlocks.add(schnelleExponentiation.pow(b, pubE, modN));
                    }
                    long t1 = System.nanoTime();

                    // Decrypt (alle Blöcke)
                    for (BigInteger c : encBlocks) {
                        schnelleExponentiation.pow(c, privD, modN);
                    }
                    long t2 = System.nanoTime();

                    enc.add((t1 - t0) / 1_000_000.0);
                    dec.add((t2 - t1) / 1_000_000.0);
                }

                // Reduce auf Rank 0 (sum/sumSq/n)
                double[] send = new double[]{
                        enc.sum, enc.sumSq, enc.n,
                        dec.sum, dec.sumSq, dec.n
                };
                double[] recv = new double[send.length];
                comm.Reduce(send, 0, recv, 0, send.length, MPI.DOUBLE, MPI.SUM, 0);

                if (rank == 0) {
                    Stats E = new Stats(); E.sum = recv[0]; E.sumSq = recv[1]; E.n = (long) recv[2];
                    Stats D = new Stats(); D.sum = recv[3]; D.sumSq = recv[4]; D.n = (long) recv[5];

                    System.out.println("---------- ");
                    System.out.println("Anzahl der Wiederholungen: " + reps);
                    System.out.printf(Locale.ROOT,
                            "Durchschnittliche Verschlüsselungszeit: %.3f ms (Standardabweichung: %.9f)%n",
                            E.mean(), E.sd());
                    System.out.printf(Locale.ROOT,
                            "Durchschnittliche Entschlüsselungszeit: %.3f ms (Standardabweichung: %.9f)%n%n",
                            D.mean(), D.sd());
                    System.out.println("---------- ");
                }

                comm.Barrier();
            }

            if (rank == 0) System.out.println();
        }

        MPI.Finalize();
    }
}
