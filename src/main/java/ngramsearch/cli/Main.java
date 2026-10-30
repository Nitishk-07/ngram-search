package ngramsearch.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ngramsearch.index.Document;
import ngramsearch.index.DocumentLoader;
import ngramsearch.index.InvertedIndex;
import ngramsearch.search.ResultGroup;
import ngramsearch.search.ScoredDocument;
import ngramsearch.search.SearchConfig;
import ngramsearch.search.SearchEngine;
import ngramsearch.search.SearchResponse;
import ngramsearch.search.TermMatch;

/**
 * Command line interface.
 *
 * <pre>
 * java -jar target/ngram-search.jar --dir samples
 * java -jar target/ngram-search.jar --dir samples --n 4 --threshold 0.6 --query "dijsktra shortest path"
 * </pre>
 */
public final class Main {

    private static final String USAGE = """
            Usage: java -jar ngram-search.jar [options]

              --dir <path>        directory of .txt files to index (default: samples)
              --n <int>           character n-gram size (default: 3)
              --shingle <int>     word shingle size for duplicate detection (default: 3)
              --threshold <0..1>  collapse threshold, shingle Jaccard (default: 0.5)
              --no-collapse       disable near-duplicate collapsing
              --weight <0..1>     weight of the n-gram score; BM25 gets 1 - weight (default: 0.5)
              --top <int>         number of results (default: 10)
              --query <text>      run one query and exit instead of starting the interactive prompt
              --no-color          plain output without ANSI colors
              --help              show this message
            """;

    private static final String COMMANDS = """
            Commands:
              :n <int>             rebuild the index with a different n-gram size
              :threshold <0..1>    set the collapse threshold
              :collapse on|off     enable or disable collapsing
              :weight <0..1>       set the n-gram score weight
              :top <int>           number of results
              :stats               index statistics and current settings
              :help                this list
              :quit                exit
            Anything else is a search query.
            """;

    private final PrintStream out;
    private final Ansi c;
    private final List<Document> docs;
    private final Path dir;
    private final int shingleSize;
    private SearchEngine engine;

    private Main(PrintStream out, boolean color, Path dir, List<Document> docs, int n, int shingleSize,
                 SearchConfig config) {
        this.out = out;
        this.c = new Ansi(color);
        this.dir = dir;
        this.docs = docs;
        this.shingleSize = shingleSize;
        this.engine = new SearchEngine(buildIndex(n), config);
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of("samples");
        int n = 3;
        int shingle = 3;
        SearchConfig config = SearchConfig.defaults();
        String query = null;
        boolean color = System.console() != null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--dir" -> dir = Path.of(value(args, ++i));
                    case "--n" -> n = Integer.parseInt(value(args, ++i));
                    case "--shingle" -> shingle = Integer.parseInt(value(args, ++i));
                    case "--threshold" -> config = config.withCollapseThreshold(Double.parseDouble(value(args, ++i)));
                    case "--no-collapse" -> config = config.withCollapse(false);
                    case "--weight" -> config = config.withNgramWeight(Double.parseDouble(value(args, ++i)));
                    case "--top" -> config = config.withTopK(Integer.parseInt(value(args, ++i)));
                    case "--query" -> query = value(args, ++i);
                    case "--no-color" -> color = false;
                    case "--color" -> color = true;
                    case "--help", "-h" -> {
                        System.out.print(USAGE);
                        return;
                    }
                    default -> throw new IllegalArgumentException("unknown option " + args[i]);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.print(USAGE);
            System.exit(2);
            return;
        }

        List<Document> docs;
        try {
            docs = DocumentLoader.loadDirectory(dir);
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
            return;
        }

        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Main app = new Main(out, color, dir, docs, n, shingle, config);
        if (query != null) {
            app.runQuery(query);
        } else {
            app.repl();
        }
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException(args[i - 1] + " needs a value");
        }
        return args[i];
    }

    private InvertedIndex buildIndex(int n) {
        long t0 = System.nanoTime();
        InvertedIndex index = InvertedIndex.build(docs, n, shingleSize);
        double ms = (System.nanoTime() - t0) / 1e6;
        out.printf("%sIndexed %d documents from %s in %.1f ms%s  (n=%d, %d words, %d distinct n-grams)%n",
                c.dim(), index.numDocs(), dir, ms, c.reset(), n, index.vocabularySize(), index.ngramCount());
        return index;
    }

    private void repl() throws IOException {
        out.println();
        out.println(c.bold() + "  N-GRAM SEARCH" + c.reset() + c.dim() + "  typo-tolerant, BM25-ranked, duplicates collapsed"
                + c.reset());
        out.println(c.dim() + "  type a query, or :help for commands" + c.reset());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            out.print(c.cyan() + "\nsearch> " + c.reset());
            out.flush();
            String line = in.readLine();
            if (line == null) {
                return;
            }
            // Windows shells sometimes prepend a byte-order mark to piped input, which would
            // otherwise turn ":stats" into a search for "stats"
            line = line.replace("﻿", "").strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(":")) {
                if (!command(line)) {
                    return;
                }
            } else {
                runQuery(line);
            }
        }
    }

    /** Returns false when the user asked to quit. */
    private boolean command(String line) {
        String[] parts = line.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1].strip() : "";
        SearchConfig cfg = engine.config();
        try {
            switch (parts[0]) {
                case ":quit", ":q", ":exit" -> {
                    return false;
                }
                case ":help" -> out.print(COMMANDS);
                case ":n" -> engine = new SearchEngine(buildIndex(Integer.parseInt(arg)), cfg);
                case ":threshold" -> setConfig(cfg.withCollapseThreshold(Double.parseDouble(arg)));
                case ":collapse" -> setConfig(cfg.withCollapse(parseOnOff(arg)));
                case ":weight" -> setConfig(cfg.withNgramWeight(Double.parseDouble(arg)));
                case ":top" -> setConfig(cfg.withTopK(Integer.parseInt(arg)));
                case ":stats" -> stats();
                default -> out.println("unknown command " + parts[0] + " (try :help)");
            }
        } catch (IllegalArgumentException e) {
            out.println(c.red() + "error: " + e.getMessage() + c.reset());
        }
        return true;
    }

    private static boolean parseOnOff(String arg) {
        return switch (arg) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> throw new IllegalArgumentException("expected on or off");
        };
    }

    private void setConfig(SearchConfig cfg) {
        engine = new SearchEngine(engine.index(), cfg);
        out.println(c.dim() + "ok" + c.reset());
    }

    private void stats() {
        InvertedIndex idx = engine.index();
        SearchConfig cfg = engine.config();
        out.printf("documents %d | vocabulary %d | distinct n-grams %d | avg length %.1f words%n",
                idx.numDocs(), idx.vocabularySize(), idx.ngramCount(), idx.avgDocLength());
        out.printf("n=%d shingle=%d | weight ngram %.2f / bm25 %.2f | min term similarity %.2f%n",
                idx.n(), idx.shingleSize(), cfg.ngramWeight(), 1 - cfg.ngramWeight(), cfg.minTermSimilarity());
        out.printf("collapse %s, threshold %.2f | top %d%n",
                cfg.collapseEnabled() ? "on" : "off", cfg.collapseThreshold(), cfg.topK());
    }

    private void runQuery(String query) {
        SearchResponse r = engine.search(query);
        double ms = r.elapsedNanos() / 1e6;
        if (r.isEmpty()) {
            out.printf("%sno results%s %s(%.2f ms)%s%n", c.yellow(), c.reset(), c.dim(), ms, c.reset());
            return;
        }
        int hidden = r.groups().stream().mapToInt(ResultGroup::collapsedCount).sum();
        out.printf("%s%d matching documents, %d candidates, %d shown%s, %d collapsed  (%.2f ms)%s%n",
                c.dim(), r.matchedDocs(), r.candidates(), r.groups().size(),
                engine.config().collapseEnabled() ? "" : " (collapsing off)", hidden, ms, c.reset());

        double w = engine.config().ngramWeight();
        int rank = 1;
        for (ResultGroup g : r.groups()) {
            ScoredDocument d = g.representative();
            out.printf("%n%s%2d. %s%s  %s%.3f%s%n", c.bold(), rank++, d.name(), c.reset(), c.green(), d.score(),
                    c.reset());
            out.printf("    %s%s%s%n", c.dim(), d.explain(w), c.reset());
            out.printf("    %s%n", snippet(d));
            if (g.collapsedCount() > 0) {
                StringBuilder sb = new StringBuilder();
                for (ResultGroup.Collapsed col : g.collapsed()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(String.format("%s [id %d, J=%.2f]", col.doc().name(), col.doc().docId(),
                            col.similarity()));
                }
                out.printf("    %s+%d near-duplicate%s collapsed: %s%s%n", c.magenta(), g.collapsedCount(),
                        g.collapsedCount() == 1 ? "" : "s", sb, c.reset());
            }
        }
    }

    /** About 140 characters of the original text around the first matched word, highlighted. */
    private String snippet(ScoredDocument d) {
        String text = engine.index().document(d.docId()).text().replaceAll("\\s+", " ").strip();
        int at = -1;
        String hit = null;
        for (TermMatch m : d.matches()) {
            Matcher mt = Pattern.compile("(?iu)\\b" + Pattern.quote(m.matchedTerm()) + "\\b").matcher(text);
            if (mt.find() && (at < 0 || mt.start() < at)) {
                at = mt.start();
                hit = text.substring(mt.start(), mt.end());
            }
        }
        if (at < 0) {
            return text.length() > 140 ? text.substring(0, 140) + "..." : text;
        }
        int from = Math.max(0, at - 60);
        int to = Math.min(text.length(), at + 80);
        String before = text.substring(from, at);
        String after = text.substring(at + hit.length(), to);
        return (from > 0 ? "..." : "") + before + c.yellow() + c.bold() + hit + c.reset() + after
                + (to < text.length() ? "..." : "");
    }

    /** ANSI escape codes, or empty strings when color is off. */
    private record Ansi(boolean on) {
        String bold() { return on ? "\u001b[1m" : ""; }
        String dim() { return on ? "\u001b[2m" : ""; }
        String green() { return on ? "\u001b[32m" : ""; }
        String yellow() { return on ? "\u001b[33m" : ""; }
        String cyan() { return on ? "\u001b[36m" : ""; }
        String magenta() { return on ? "\u001b[35m" : ""; }
        String red() { return on ? "\u001b[31m" : ""; }
        String reset() { return on ? "\u001b[0m" : ""; }
    }
}
