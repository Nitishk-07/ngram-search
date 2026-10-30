package ngramsearch.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ngramsearch.index.Document;
import ngramsearch.index.InvertedIndex;
import ngramsearch.search.ResultGroup;
import ngramsearch.search.SearchConfig;
import ngramsearch.search.SearchEngine;
import ngramsearch.search.SearchResponse;

/**
 * Measures indexing time and query latency with and without collapsing on generated corpora.
 * Every number printed is measured on the machine running it.
 *
 * <pre>
 * java -cp target/ngram-search.jar ngramsearch.bench.Benchmark [--sizes 1000,5000,20000] [--queries 300]
 * </pre>
 */
public final class Benchmark {

    public static void main(String[] args) {
        int[] sizes = {1000, 5000, 20000};
        int numQueries = 300;
        int warmup = 200;
        long seed = 42;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--sizes" -> sizes = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray();
                case "--queries" -> numQueries = Integer.parseInt(args[++i]);
                case "--warmup" -> warmup = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }

        System.out.printf("Java %s, %s %s, %d cores, max heap %d MB%n", System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() >> 20);
        System.out.printf("seed %d, %d timed queries per mode after %d warm-up queries, n=3, 10%% near-duplicates%n%n",
                seed, numQueries, warmup);
        System.out.println("| docs | index build (median of 3) | mode | mean | p50 | p95 | avg results shown | avg collapsed |");
        System.out.println("|---:|---:|---|---:|---:|---:|---:|---:|");

        for (int size : sizes) {
            CorpusGenerator gen = new CorpusGenerator(seed, 8000);
            List<Document> docs = gen.documents(size, 0.10);
            List<String> queries = gen.queries(docs, numQueries + warmup);

            double[] builds = new double[3];
            InvertedIndex index = null;
            for (int r = 0; r < builds.length; r++) {
                long t0 = System.nanoTime();
                index = InvertedIndex.build(docs, 3, 3);
                builds[r] = (System.nanoTime() - t0) / 1e6;
            }
            Arrays.sort(builds);

            SearchEngine on = new SearchEngine(index, SearchConfig.defaults());
            SearchEngine off = new SearchEngine(index, SearchConfig.defaults().withCollapse(false));
            for (int q = 0; q < warmup; q++) {
                on.search(queries.get(q));
                off.search(queries.get(q));
            }

            List<String> timed = queries.subList(warmup, queries.size());
            Stats offStats = new Stats();
            Stats onStats = new Stats();
            for (int q = 0; q < timed.size(); q++) {
                // alternate which mode goes first so neither always gets a warmer cache
                if (q % 2 == 0) {
                    offStats.add(off.search(timed.get(q)));
                    onStats.add(on.search(timed.get(q)));
                } else {
                    onStats.add(on.search(timed.get(q)));
                    offStats.add(off.search(timed.get(q)));
                }
            }
            System.out.printf("| %,d | %.0f ms | no collapse | %s |%n", size, builds[1], offStats);
            System.out.printf("| %,d | | collapse (t=0.5) | %s |%n", size, onStats);
        }
    }

    private static final class Stats {
        private final List<Long> nanos = new ArrayList<>();
        private long shown;
        private long collapsed;

        void add(SearchResponse r) {
            nanos.add(r.elapsedNanos());
            shown += r.groups().size();
            collapsed += r.groups().stream().mapToInt(ResultGroup::collapsedCount).sum();
        }

        @Override
        public String toString() {
            long[] sorted = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
            double mean = Arrays.stream(sorted).average().orElse(0) / 1e6;
            double p50 = sorted[(int) (0.50 * (sorted.length - 1))] / 1e6;
            double p95 = sorted[(int) (0.95 * (sorted.length - 1))] / 1e6;
            return String.format("%.3f ms | %.3f ms | %.3f ms | %.2f | %.2f", mean, p50, p95,
                    (double) shown / sorted.length, (double) collapsed / sorted.length);
        }
    }
}
