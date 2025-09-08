package org.example.mpjbench;

import mpi.Intracomm;
import mpi.MPI;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class RSATextBenchmarkAliceBob {
    static {
        try {
            System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err)), true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    static final class Key { final BigInteger n,e,d; Key(BigInteger n, BigInteger e, BigInteger d){this.n=n;this.e=e;this.d=d;} }

    // n/e/d aus Textdateien lesen (dezimal oder hex mit/ohne 0x)
    static BigInteger readBigInt(String path) throws IOException {
        String s = Files.readString(Paths.get(path), StandardCharsets.UTF_8).trim()
                .replaceAll("\\s+", ""); // Whitespaces raus
        String t = s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
        boolean looksHex = t.matches("(?i)[0-9a-f]+") && (t.matches(".*[a-fA-F].*") || s.startsWith("0x") || s.startsWith("0X"));
        return new BigInteger(looksHex ? t : s, looksHex ? 16 : 10);
    }

    static Key loadKeyTxt(String nPath, String ePath, String dPath) throws IOException {
        return new Key(readBigInt(nPath), readBigInt(ePath), readBigInt(dPath));
    }

    // Blöcke < n (ohne Padding; reine modPow-Messung)
    static BigInteger[] encodeBlocks(byte[] data, BigInteger n){
        int nBytes = (n.bitLength()-1)/8;            // strikt < n
        int blockSize = Math.max(1, nBytes - 1);     // kleine Marge
        int blocks = (data.length + blockSize - 1)/blockSize;
        BigInteger[] out = new BigInteger[blocks];
        for (int i=0;i<blocks;i++){
            int start=i*blockSize, end=Math.min(data.length,start+blockSize);
            byte[] chunk = Arrays.copyOfRange(data,start,end);
            if (chunk.length>0) chunk[0] = (byte)(chunk[0] & 0x7F);   // positiv
            BigInteger m = new BigInteger(1, chunk);
            if (m.compareTo(n)>=0) m = m.mod(n.subtract(BigInteger.ONE)).add(BigInteger.ONE);
            out[i]=m;
        }
        return out;
    }

    static final class Stats { double sum=0,sumSq=0; long count=0; }
    static void add(Stats s,double v){ s.sum+=v; s.sumSq+=v*v; s.count++; }

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();
        int size = comm.Size();

        // CLI
        String aliceN=null, aliceE=null, aliceD=null, bobN=null, bobE=null, bobD=null;
        int[] reps = new int[]{100,250,500};

        for (int i=0;i<args.length;i++){
            switch (args[i]){
                case "--alice-n": aliceN = args[++i]; break;
                case "--alice-e": aliceE = args[++i]; break;
                case "--alice-d": aliceD = args[++i]; break;
                case "--bob-n":   bobN   = args[++i]; break;
                case "--bob-e":   bobE   = args[++i]; break;
                case "--bob-d":   bobD   = args[++i]; break;
                case "--reps":    reps   = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray(); break;
            }
        }
        if (rank==0 && (aliceN==null||aliceE==null||aliceD==null||bobN==null||bobE==null||bobD==null)){
            System.err.println("Fehler: --alice-n/e/d und --bob-n/e/d müssen angegeben werden.");
            MPI.Finalize(); return;
        }

        // Rank 0 lädt Keys, broadcastet n/e/d an alle
        BigInteger[] pack = new BigInteger[6];
        if (rank==0){
            Key alice = loadKeyTxt(aliceN, aliceE, aliceD);
            Key bob   = loadKeyTxt(bobN,   bobE,   bobD);
            pack = new BigInteger[]{alice.n,alice.e,alice.d, bob.n,bob.e,bob.d};
        }
        comm.Bcast(pack, 0, pack.length, MPI.OBJECT, 0);
        Key alice = new Key(pack[0],pack[1],pack[2]);
        Key bob   = new Key(pack[3],pack[4],pack[5]);

        // Texte (UTF-8, exakt wie gewünscht)
        final String text1 = "„Möge die Macht mit dir sein!“.";
        final String text2 = "„Luke Skywalker entdeckt, dass er Teil einer uralten Machttradition ist. Als er sich der Rebellenallianz anschließt, kämpft er gegen das galaktische Imperium, das die Galaxis unterdrückt. Mit Hilfe von Obi-Wan Kenobi, Prinzessin Leia und Han Solo begibt er sich auf eine gefährliche Mission, um den Todesstern zu zerstören und die Freiheit in der Galaxis wiederherzustellen.“";
        final String text3 = "„In einer weit, weit entfernten Galaxis entfaltet sich eine epische Saga. Die Geschichte beginnt mit der tyrannischen Herrschaft des Imperiums, das unter der Führung von Darth Vader und dem finsteren Imperator über unzählige Welten herrscht. In dieser düsteren Zeit lebt Luke Skywalker, ein junger Farmer auf dem Wüstenplaneten Tatooine, der von einer geheimnisvollen Macht angezogen wird. Als er den alten Jedi-Meister Obi-Wan Kenobi trifft, erfährt er von seinem wahren Erbe und der Macht, die in ihm schlummert. Luke schließt sich einer kleinen, aber entschlossenen Rebellenallianz an, angeführt von Prinzessin Leia, deren Tapferkeit und Visionen von Freiheit die Hoffnung in den Herzen der Unterdrückten neu entfachen. Gemeinsam mit dem charmanten Schmuggler Han Solo und seinem treuen Co-Piloten Chewbacca begibt sich Luke auf eine gefährliche Mission: den Todesstern, eine gewaltige Raumstation mit der Fähigkeit, ganze Planeten zu vernichten, zu zerstören. Während die Rebellen hinter geheimen Plänen her sind, die die Schwachstelle des Todessterns enthüllen, entbrennt ein Kampf zwischen Licht und Dunkelheit. Darth Vader, einst ein vielversprechender Jedi, wurde von der dunklen Seite der Macht verführt und dient nun dem Imperium. In epischen Duellen, in denen Lichtschwerter aufeinanderprallen, wird der Glaube an die Macht auf die Probe gestellt. Im Laufe der Geschichte wachsen die Helden über sich hinaus und lernen, dass der Schlüssel zur Freiheit nicht nur in der Macht liegt, sondern auch im Mut, die Wahrheit zu suchen und für das Richtige zu kämpfen. Die Rebellion stellt sich den scheinbar unbezwingbaren Kräften des Imperiums entgegen und zeigt, dass selbst in den dunkelsten Zeiten das Licht der Hoffnung niemals erlischt. Diese Geschichte ist mehr als ein Kampf zwischen Gut und Böse – sie ist ein Zeugnis von Loyalität, Freundschaft und der unerschütterlichen Überzeugung, dass jeder Einzelne das Schicksal der Galaxis verändern kann.“";
        String[] texts = {text1, text2, text3};
        byte[][] textsUtf8 = Arrays.stream(texts).map(s -> s.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);

        if (rank==0){
            System.out.println("Prozesse: " + size + " | Alice n: " + alice.n.bitLength() + " Bit | Bob n: " + bob.n.bitLength() + " Bit\n");
        }

        // vorab Blöcke für beide Moduli
        BigInteger[][] blocksAliceN = new BigInteger[texts.length][];
        BigInteger[][] blocksBobN   = new BigInteger[texts.length][];
        for (int i=0;i<texts.length;i++){
            blocksAliceN[i] = encodeBlocks(textsUtf8[i], alice.n);
            blocksBobN[i]   = encodeBlocks(textsUtf8[i], bob.n);
        }

        // Warmup
        for (int i=0;i<3;i++){
            for (BigInteger m: blocksBobN[0])  { m.modPow(bob.e, bob.n);  m.modPow(bob.d, bob.n); }
            for (BigInteger m: blocksAliceN[0]){ m.modPow(alice.e,alice.n);m.modPow(alice.d,alice.n); }
        }
        comm.Barrier();

        bench(comm, "Alice to Bob (Bob.e/Bob.d)", blocksBobN,   bob.e,   bob.n,   bob.d,   reps, textsUtf8);
        bench(comm, "Bob to Alice (Alice.e/Alice.d)", blocksAliceN, alice.e, alice.n, alice.d, reps, textsUtf8);

        MPI.Finalize();
    }

    private static void bench(Intracomm comm, String label, BigInteger[][] textBlocks,
                              BigInteger pubE, BigInteger modN, BigInteger privD,
                              int[] repsList, byte[][] textsUtf8){
        int rank = comm.Rank();
        if (rank==0) System.out.println("==== " + label + " ====");
        for (int ti=0; ti<textBlocks.length; ti++){
            if (rank==0) System.out.println("== Text "+(ti+1)+" | Länge: "+textsUtf8[ti].length+" Byte | Blöcke: "+textBlocks[ti].length+" ==");
            for (int reps : repsList){
                int size = comm.Size();
                int base = reps/size, rest = reps%size, localReps = base + (rank<rest?1:0);
                Stats enc = new Stats(), dec = new Stats();

                for (int r=0;r<localReps;r++){
                    long t0=System.nanoTime();
                    BigInteger[] c = new BigInteger[textBlocks[ti].length];
                    for (int b=0;b<textBlocks[ti].length;b++) c[b] = textBlocks[ti][b].modPow(pubE, modN);
                    long t1=System.nanoTime();
                    for (BigInteger x: c) x.modPow(privD, modN);
                    long t2=System.nanoTime();
                    add(enc,(t1-t0)/1_000_000.0); add(dec,(t2-t1)/1_000_000.0);
                }

                double[] sendE = new double[]{enc.sum,enc.sumSq,enc.count}, recvE = new double[3];
                double[] sendD = new double[]{dec.sum,dec.sumSq,dec.count}, recvD = new double[3];
                comm.Reduce(sendE,0,recvE,0,3,MPI.DOUBLE,MPI.SUM,0);
                comm.Reduce(sendD,0,recvD,0,3,MPI.DOUBLE,MPI.SUM,0);

                if (rank==0){
                    double nE=recvE[2], meanE=recvE[0]/nE, sdE=Math.sqrt(Math.max(0, recvE[1]/nE - meanE*meanE));
                    double nD=recvD[2], meanD=recvD[0]/nD, sdD=Math.sqrt(Math.max(0, recvD[1]/nD - meanD*meanD));
                    System.out.println("---------- ");
                    System.out.println("Anzahl der Wiederholungen: " + reps);
                    System.out.printf("Durchschnittliche Verschlüsselungszeit: %.3f ms (Standardabweichung: %.9f)%n", meanE, sdE);
                    System.out.printf("Durchschnittliche Entschlüsselungszeit: %.3f ms (Standardabweichung: %.9f)%n%n", meanD, sdD);
                    System.out.println("---------- ");
                }
                comm.Barrier();
            }
            if (rank==0) System.out.println();
        }
    }
}
