import java.util.*;
import java.util.function.Function;

/** Java-only reproducible comparison of the three exact-search algorithms.
 *  Compile after tools/run.py --setup:
 *    javac --release 17 -cp Java/build -d Java/build tests/student/BenchmarkSearch.java
 *  Run:
 *    java -cp Java/build BenchmarkSearch
 */
public final class BenchmarkSearch {
    private static final long SEED = 20261108L;
    private static final int[][] SIZES = {{300, 16}, {900, 48}, {2400, 96}};
    private static final int WARMUP = 1500, REPETITIONS = 250, RUNS = 3;
    private static volatile long checksum;

    private static Map<String, Object> fixture(String mode, int n, int m) {
        String text, pattern;
        if (mode.equals("random")) {
            Random random = new Random(SEED + n * 10000L + m);
            StringBuilder builder = new StringBuilder(n);
            for (int i = 0; i < n; i++) builder.append((char) ('a' + random.nextInt(26)));
            text = builder.toString();
            pattern = text.substring(n / 3, n / 3 + m);
        } else {
            text = "a".repeat(n);
            pattern = "a".repeat(m);
        }
        return Map.of("text", text, "pattern", pattern);
    }

    private static List<Integer> reference(Map<String, Object> request) {
        String text = (String) request.get("text");
        String pattern = (String) request.get("pattern");
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i + pattern.length() <= text.length(); i++) {
            // The library routine is used in the independent oracle ONLY.
            if (text.regionMatches(i, pattern, 0, pattern.length())) positions.add(i);
        }
        return positions;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> positions(Map<String, Object> response) {
        return (List<Integer>) response.get("positions");
    }

    private static Function<Map<String, Object>, Map<String, Object>> method(String name) {
        switch (name) {
            case "naive": return Algorithms::naive;
            case "kmp": return Algorithms::kmp;
            case "rk": return Algorithms::rk;
            default: throw new IllegalArgumentException(name);
        }
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.out.println("Seed=" + SEED + "; warm-up=" + WARMUP
                + "; runs=" + RUNS + "; calls/run=" + REPETITIONS + "; ms/call");
        System.out.println("|Case|n|m|Matches|Algorithm|Run 1|Run 2|Run 3|Median|Verified|");
        System.out.println("|---|---:|---:|---:|---|---:|---:|---:|---:|---|");
        for (String mode : new String[] {"random", "repetitive"}) {
            for (int[] size : SIZES) {
                int n = size[0], m = size[1];
                Map<String, Object> request = fixture(mode, n, m);
                List<Integer> expected = reference(request);
                for (String name : new String[] {"naive", "kmp", "rk"}) {
                    Function<Map<String, Object>, Map<String, Object>> algorithm = method(name);
                    if (!positions(algorithm.apply(request)).equals(expected)) {
                        throw new AssertionError("Incorrect search result for " + mode + " " + name);
                    }
                    for (int i = 0; i < WARMUP; i++) checksum += positions(algorithm.apply(request)).size();
                    double[] samples = new double[RUNS];
                    for (int run = 0; run < RUNS; run++) {
                        long started = System.nanoTime();
                        for (int iteration = 0; iteration < REPETITIONS; iteration++) {
                            checksum += positions(algorithm.apply(request)).size();
                        }
                        samples[run] = (System.nanoTime() - started) / (REPETITIONS * 1_000_000.0);
                    }
                    double[] sorted = samples.clone();
                    Arrays.sort(sorted);
                    System.out.printf("|%s|%d|%d|%d|%s|%.4f|%.4f|%.4f|%.4f|yes|%n",
                            mode, n, m, expected.size(), name,
                            samples[0], samples[1], samples[2], sorted[1]);
                }
            }
        }
    }
}
