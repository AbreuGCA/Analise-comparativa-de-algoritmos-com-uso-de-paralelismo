// WordCountMain.java
// Compile / Run notes:
// - On Windows (PowerShell / cmd): use semicolon in classpath and compile both sources
//     javac -cp ".;path\to\jocl-2.0.4.jar" WordCountMain.java GPUWordCounter.java
//     java  -cp ".;path\to\jocl-2.0.4.jar" WordCountMain <mode> <inputFile> <targetWord> <runs> <outputCsv>
// mode = serial | cpu | gpu | all
// runs = número de repetições (ex: 3)

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public class WordCountMain {

    // ---------- Serial ----------
    public static Result countSerial(String text, String target) {
        long t0 = System.currentTimeMillis();
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += 1; // next possible match (allow overlapping)
        }
        long t1 = System.currentTimeMillis();
        return new Result(count, t1 - t0);
    }

    // ---------- Parallel CPU ----------
    public static Result countParallelCPU(String[] lines, String target, int threads) throws InterruptedException, ExecutionException {
        long t0 = System.currentTimeMillis();
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        int n = lines.length;
        int chunk = Math.max(1, n / threads);
        for (int i = 0; i < n; i += chunk) {
            int start = i;
            int end = Math.min(n, i + chunk);
            futures.add(ex.submit(() -> {
                int local = 0;
                for (int j = start; j < end; j++) {
                    String line = lines[j];
                    int idx = 0;
                    while ((idx = line.indexOf(target, idx)) != -1) {
                        local++;
                        idx += 1;
                    }
                }
                return local;
            }));
        }
        int total = 0;
        for (Future<Integer> f : futures) {
            total += f.get();
        }
        ex.shutdown();
        long t1 = System.currentTimeMillis();
        return new Result(total, t1 - t0);
    }

    // ---------- Parallel GPU (JOCL / OpenCL) ----------
    // NOTE: This part requires JOCL on the classpath and a working OpenCL driver.
    // The kernel loads the input bytes and performs naive matching for each possible start index.
    // For simplicity it assumes ASCII/UTF-8 and uses bytes.
    // We'll implement GPU counting only if JOCL is available at runtime.
    public static Result countParallelGPU(byte[] textBytes, byte[] targetBytes) {
        // Try to load JOCL classes dynamically to avoid compile-time crash if jocl not on classpath.
        try {
            return GPUWordCounter.countWithJOCL(textBytes, targetBytes);
        } catch (Throwable e) {
            System.err.println("GPU counting failed: " + e.getMessage());
            e.printStackTrace();
            return new Result(-1, -1); // indicate failure
        }
    }

    // ---------- Helper result ----------
    public static class Result {

        public final int count;
        public final long millis;

        public Result(int c, long m) {
            count = c;
            millis = m;
        }

        public String toCsvLine(String method, String file, String target, int run) {
            return String.join(",", Arrays.asList(method, file, target, Integer.toString(count), Long.toString(millis), Integer.toString(run)));
        }
    }

    // ---------- Main: CLI and runner ----------
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.out.println("Usage: java -cp \".:jocl-2.0.4.jar\" WordCountMain <mode> <inputFile> <targetWord> <runs> <outputCsv>");
            System.out.println("mode = serial | cpu | gpu | all");
            return;
        }
        String mode = args[0];
        String inputFile = args[1];
        String target = args[2];
        int runs = Integer.parseInt(args[3]);
        String outputCsv = args[4];

        String text = new String(Files.readAllBytes(Paths.get(inputFile)), StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n");
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);

        // Cria diretório pai do CSV caso não exista
        Path outPath = Paths.get(outputCsv);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }

        // Prepare CSV header
        try (BufferedWriter csv = Files.newBufferedWriter(Paths.get(outputCsv), StandardCharsets.UTF_8)) {
            csv.write("method,file,target,count,millis,run\n");
            if (mode.equals("serial") || mode.equals("all")) {
                for (int r = 1; r <= runs; r++) {
                    Result res = countSerial(text, target);
                    csv.write(res.toCsvLine("SerialCPU", inputFile, target, r) + "\n");
                    System.out.println("Serial: " + res.count + " ocorrências em " + res.millis + " ms");
                }
            }
            if (mode.equals("cpu") || mode.equals("all")) {
                int threads = Runtime.getRuntime().availableProcessors();
                System.out.println("Parallel CPU -> usando threads: " + threads);
                for (int r = 1; r <= runs; r++) {
                    Result res = countParallelCPU(lines, target, threads);
                    csv.write(res.toCsvLine("ParallelCPU", inputFile, target, r) + "\n");
                    System.out.println("ParallelCPU: " + res.count + " ocorrências em " + res.millis + " ms");
                }
            }
            if (mode.equals("gpu") || mode.equals("all")) {
                for (int r = 1; r <= runs; r++) {
                    Result res = countParallelGPU(textBytes, targetBytes);
                    csv.write(res.toCsvLine("ParallelGPU", inputFile, target, r) + "\n");
                    System.out.println("ParallelGPU: " + res.count + " ocorrências em " + res.millis + " ms");
                }
            }
            System.out.println("CSV gravado em: " + outputCsv);
        }
    }
}
