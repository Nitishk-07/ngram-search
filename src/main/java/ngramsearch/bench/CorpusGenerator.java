package ngramsearch.bench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import ngramsearch.index.Document;

/**
 * Deterministic synthetic corpus for benchmarking.
 *
 * <p>Words are made-up syllable combinations ("tolakim", "vesuran"), drawn with a Zipf
 * distribution so a few words are very common and most are rare, like real text. A fraction of
 * documents are near-duplicates: copies of an earlier document with a few words swapped.
 */
public final class CorpusGenerator {

    private static final String[] SYLLABLES = {
            "ka", "lo", "mi", "ra", "su", "te", "vo", "ne", "pa", "ri", "do", "ku", "sa", "le", "mo", "ti",
            "ba", "ze", "fi", "go", "an", "el", "is", "or", "un", "ve", "ha", "ji", "qu", "wy"};

    private final Random rng;
    private final List<String> vocabulary;
    private final double[] cumulative;

    public CorpusGenerator(long seed, int vocabularySize) {
        this.rng = new Random(seed);
        Set<String> words = new HashSet<>();
        List<String> vocab = new ArrayList<>();
        while (vocab.size() < vocabularySize) {
            int syllables = 2 + rng.nextInt(3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < syllables; i++) {
                sb.append(SYLLABLES[rng.nextInt(SYLLABLES.length)]);
            }
            if (words.add(sb.toString())) {
                vocab.add(sb.toString());
            }
        }
        this.vocabulary = vocab;
        this.cumulative = new double[vocabularySize];
        double sum = 0;
        for (int r = 0; r < vocabularySize; r++) {
            sum += 1.0 / Math.pow(r + 1, 1.07);
            cumulative[r] = sum;
        }
        for (int r = 0; r < vocabularySize; r++) {
            cumulative[r] /= sum;
        }
    }

    private String sampleWord() {
        double u = rng.nextDouble();
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] < u) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return vocabulary.get(lo);
    }

    /**
     * @param numDocs       number of documents
     * @param duplicateRate fraction of documents that are edited copies of an earlier one
     */
    public List<Document> documents(int numDocs, double duplicateRate) {
        List<Document> docs = new ArrayList<>(numDocs);
        List<String[]> tokens = new ArrayList<>(numDocs);
        for (int i = 0; i < numDocs; i++) {
            String[] words;
            if (i > 0 && rng.nextDouble() < duplicateRate) {
                words = tokens.get(rng.nextInt(i)).clone();
                int edits = Math.max(1, words.length / 25);   // change about 4% of the words
                for (int e = 0; e < edits; e++) {
                    words[rng.nextInt(words.length)] = sampleWord();
                }
            } else {
                words = new String[60 + rng.nextInt(181)];   // 60 to 240 words
                for (int w = 0; w < words.length; w++) {
                    words[w] = sampleWord();
                }
            }
            tokens.add(words);
            docs.add(new Document(String.format("doc-%06d.txt", i), String.join(" ", words)));
        }
        return docs;
    }

    /** Queries of 1 to 3 words taken from random documents; about half get one typo. */
    public List<String> queries(List<Document> docs, int count) {
        List<String> queries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String[] words = docs.get(rng.nextInt(docs.size())).text().split(" ");
            int len = 1 + rng.nextInt(3);
            List<String> q = new ArrayList<>();
            for (int k = 0; k < len; k++) {
                q.add(words[rng.nextInt(words.length)]);
            }
            if (rng.nextBoolean()) {
                int k = rng.nextInt(q.size());
                q.set(k, typo(q.get(k)));
            }
            queries.add(String.join(" ", q));
        }
        return queries;
    }

    private String typo(String word) {
        if (word.length() < 4) {
            return word;
        }
        int p = 1 + rng.nextInt(word.length() - 2);
        StringBuilder sb = new StringBuilder(word);
        switch (rng.nextInt(3)) {
            case 0 -> sb.setCharAt(p, (char) ('a' + rng.nextInt(26)));        // substitution
            case 1 -> sb.deleteCharAt(p);                                       // deletion
            default -> {                                                        // transposition
                char t = sb.charAt(p);
                sb.setCharAt(p, sb.charAt(p - 1));
                sb.setCharAt(p - 1, t);
            }
        }
        return sb.toString();
    }
}
