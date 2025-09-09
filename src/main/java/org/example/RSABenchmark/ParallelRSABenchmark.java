package org.example.RSABenchmark;

import mpi.Intracomm;
import mpi.MPI;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.example.rsa.RSAUTF8;
import org.example.rsa.RSAUtils;

/**
 * Benchmark that runs a single RSA encryption/decryption where the
 * modular exponentiation is parallelised across MPI processes.
 * Rank 0 prepares the data and measures the timings.
 */
public class ParallelRSABenchmark {

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        Intracomm comm = MPI.COMM_WORLD;
        int rank = comm.Rank();

        String message = "In einer weit, weit entfernten Galaxis entfaltet sich eine epische Saga. Die Geschichte beginnt mit der tyrannischen Herrschaft des Imperiums, das unter der Führung von Darth Vader und dem finsteren Imperator über unzählige Welten herrscht. In dieser düsteren Zeit lebt Luke Skywalker, ein junger Farmer auf dem Wüstenplaneten Tatooine, der von einer geheimnisvollen Macht angezogen wird. Als er den alten Jedi-Meister Obi-Wan Kenobi trifft, erfährt er von seinem wahren Erbe und der Macht, die in ihm schlummert. Luke schließt sich einer kleinen, aber entschlossenen Rebellenallianz an, angeführt von Prinzessin Leia, deren Tapferkeit und Visionen von Freiheit die Hoffnung in den Herzen der Unterdrückten neu entfachen. Gemeinsam mit dem charmanten Schmuggler Han Solo und seinem treuen Co-Piloten Chewbacca begibt sich Luke auf eine gefährliche Mission: den Todesstern, eine gewaltige Raumstation mit der Fähigkeit, ganze Planeten zu vernichten, zu zerstören. Während die Rebellen hinter geheimen Plänen her sind, die die Schwachstelle des Todessterns enthüllen, entbrennt ein Kampf zwischen Licht und Dunkelheit. Darth Vader, einst ein vielversprechender Jedi, wurde von der dunklen Seite der Macht verführt und dient nun dem Imperium. In epischen Duellen, in denen Lichtschwerter aufeinanderprallen, wird der Glaube an die Macht auf die Probe gestellt. Im Laufe der Geschichte wachsen die Helden über sich hinaus und lernen, dass der Schlüssel zur Freiheit nicht nur in der Macht liegt, sondern auch im Mut, die Wahrheit zu suchen und für das Richtige zu kämpfen. Die Rebellion stellt sich den scheinbar unbezwingbaren Kräften des Imperiums entgegen und zeigt, dass selbst in den dunkelsten Zeiten das Licht der Hoffnung niemals erlischt. Diese Geschichte ist mehr als ein Kampf zwischen Gut und Böse – sie ist ein Zeugnis von Loyalität, Freundschaft und der unerschütterlichen Überzeugung, dass jeder Einzelne das Schicksal der Galaxis verändern kann.\"\n";

        BigInteger e = null, d = null, n = null;
        BigInteger[] plainBlocks = null;

        if (rank == 0) {
            RSAUtils.loadKeysFromFiles();
            e = RSAUtils.getBobPublicKey();
            d = RSAUtils.getBobPrivateKey();
            n = RSAUtils.getBobModulus();
            List<BigInteger> blocks = RSAUTF8.textToBigIntegerBlocks(message, n);
            plainBlocks = blocks.toArray(new BigInteger[0]);
            System.out.println("[Benchmark][Rank0] Bl\u00f6cke: " + plainBlocks.length + " | Prozesse: " + comm.Size());
        }

        long t0 = System.currentTimeMillis();
        BigInteger[] cipherBlocks = ParallelRSA.encrypt(plainBlocks, e, n, comm);
        long t1 = System.currentTimeMillis();
        BigInteger[] decrypted = ParallelRSA.decrypt(rank == 0 ? cipherBlocks : null, d, n, comm);
        long t2 = System.currentTimeMillis();

        if (rank == 0) {
            int blockSize = RSAUTF8.getEncryptionBlockSize(n);
            byte[] bytes = RSAUTF8.bigIntegerBlocksToBytes(Arrays.asList(decrypted), blockSize);
            String recovered = new String(bytes, StandardCharsets.UTF_8).trim();
            System.out.println("[Benchmark][Rank0] Verschl\u00fcsselung: " + (t1 - t0) + " ms");
            System.out.println("[Benchmark][Rank0] Entschl\u00fcsselung: " + (t2 - t1) + " ms");
            System.out.println("[Benchmark][Rank0] Zur\u00fcckgewonnener Text: " + recovered);
        }
        MPI.Finalize();
    }
}
