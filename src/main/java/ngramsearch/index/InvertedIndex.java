package ngramsearch.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import ngramsearch.text.NGrams;
import ngramsearch.text.TextNormalizer;

/**
 * All the lookup tables the search engine needs, built once from a list of documents.
 *
 * <pre>
 * ngram -> doc ids       candidate retrieval: which documents could match a query at all
 * ngram -> terms         fuzzy matching: which vocabulary words look like a misspelled query word
 * term  -> (doc -> tf)   BM25: how often each word occurs in each document
 * doc   -> signature     collapsing: sorted hashes of word shingles, for near-duplicate detection
 * </pre>
 */
public final class InvertedIndex {

    private static final int[] NO_DOCS = new int[0];

    private final int n;
    private final int shingleSize;
    private final List<Document> docs;
    private final List<Map<String, Integer>> termFreqs;
    private final int[] docLengths;
    private final double avgDocLength;
    private final Map<String, Map<Integer, Integer>> termPostings;
    private final Map<String, int[]> ngramDocPostings;
    private final Map<String, List<String>> ngramTermPostings;
    private final Map<String, Integer> termNgramCounts;
    private final long[][] signatures;

    private InvertedIndex(int n, int shingleSize, List<Document> docs, List<Map<String, Integer>> termFreqs,
                          int[] docLengths, double avgDocLength, Map<String, Map<Integer, Integer>> termPostings,
                          Map<String, int[]> ngramDocPostings, Map<String, List<String>> ngramTermPostings,
                          Map<String, Integer> termNgramCounts, long[][] signatures) {
        this.n = n;
        this.shingleSize = shingleSize;
        this.docs = docs;
        this.termFreqs = termFreqs;
        this.docLengths = docLengths;
        this.avgDocLength = avgDocLength;
        this.termPostings = termPostings;
        this.ngramDocPostings = ngramDocPostings;
        this.ngramTermPostings = ngramTermPostings;
        this.termNgramCounts = termNgramCounts;
        this.signatures = signatures;
    }

    public static InvertedIndex build(List<Document> documents, int n, int shingleSize) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, got " + n);
        }
        if (shingleSize < 1) {
            throw new IllegalArgumentException("shingle size must be >= 1, got " + shingleSize);
        }
        List<Document> docs = List.copyOf(documents);
        int numDocs = docs.size();
        List<Map<String, Integer>> termFreqs = new ArrayList<>(numDocs);
        int[] docLengths = new int[numDocs];
        long totalLength = 0;
        // TreeMap so the vocabulary is iterated in a fixed order: makes everything downstream deterministic
        Map<String, Map<Integer, Integer>> termPostings = new TreeMap<>();
        long[][] signatures = new long[numDocs][];
        Map<String, Set<String>> ngramCache = new HashMap<>();
        Map<String, IntList> ngramDocs = new HashMap<>();

        for (int id = 0; id < numDocs; id++) {
            List<String> allTokens = TextNormalizer.tokens(docs.get(id).text());
            Map<String, Integer> tf = new HashMap<>();
            int length = 0;
            for (String token : allTokens) {
                if (!TextNormalizer.isStopword(token)) {
                    tf.merge(token, 1, Integer::sum);
                    length++;
                }
            }
            termFreqs.add(tf);
            docLengths[id] = length;
            totalLength += length;

            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                termPostings.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(id, e.getValue());
                Set<String> grams = ngramCache.computeIfAbsent(e.getKey(), t -> NGrams.charNGramSet(t, n));
                for (String g : grams) {
                    ngramDocs.computeIfAbsent(g, k -> new IntList()).addIfNotLast(id);
                }
            }
            signatures[id] = NGrams.shingleSignature(allTokens, shingleSize);
        }

        Map<String, int[]> ngramDocPostings = new HashMap<>(ngramDocs.size() * 2);
        ngramDocs.forEach((g, ids) -> ngramDocPostings.put(g, ids.toArray()));

        Map<String, List<String>> ngramTermPostings = new HashMap<>();
        Map<String, Integer> termNgramCounts = new HashMap<>();
        for (String term : termPostings.keySet()) {
            Set<String> grams = ngramCache.get(term);
            termNgramCounts.put(term, grams.size());
            for (String g : grams) {
                ngramTermPostings.computeIfAbsent(g, k -> new ArrayList<>()).add(term);
            }
        }

        double avg = numDocs == 0 ? 0.0 : (double) totalLength / numDocs;
        return new InvertedIndex(n, shingleSize, docs, termFreqs, docLengths, avg, termPostings,
                ngramDocPostings, ngramTermPostings, termNgramCounts, signatures);
    }

    public int n() {
        return n;
    }

    public int shingleSize() {
        return shingleSize;
    }

    public int numDocs() {
        return docs.size();
    }

    public Document document(int id) {
        return docs.get(id);
    }

    public int vocabularySize() {
        return termPostings.size();
    }

    public int ngramCount() {
        return ngramDocPostings.size();
    }

    /** Sorted ids of documents containing the n-gram (inside any indexed word). */
    public int[] docsWithNgram(String ngram) {
        return ngramDocPostings.getOrDefault(ngram, NO_DOCS);
    }

    /** Vocabulary terms containing the n-gram, in alphabetical order. */
    public List<String> termsWithNgram(String ngram) {
        return ngramTermPostings.getOrDefault(ngram, Collections.emptyList());
    }

    /** Number of distinct n-grams in a vocabulary term. */
    public int termNgramCount(String term) {
        return termNgramCounts.getOrDefault(term, 0);
    }

    public int termFrequency(String term, int docId) {
        return termFreqs.get(docId).getOrDefault(term, 0);
    }

    public int documentFrequency(String term) {
        Map<Integer, Integer> postings = termPostings.get(term);
        return postings == null ? 0 : postings.size();
    }

    public boolean containsTerm(String term) {
        return termPostings.containsKey(term);
    }

    /** Number of non-stopword tokens in the document. */
    public int docLength(int docId) {
        return docLengths[docId];
    }

    public double avgDocLength() {
        return avgDocLength;
    }

    /**
     * Sorted 64-bit hashes of the document's word shingles, used for near-duplicate detection.
     * The array is shared, so callers must not modify it.
     */
    public long[] signature(int docId) {
        return signatures[docId];
    }
}
