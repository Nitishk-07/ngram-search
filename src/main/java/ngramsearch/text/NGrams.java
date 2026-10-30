package ngramsearch.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Character n-grams (for typo tolerance) and word shingles (for duplicate detection). */
public final class NGrams {

    /** Marks the start and end of a word so prefixes and suffixes get their own n-grams. */
    public static final char BOUNDARY = '$';

    private NGrams() {
    }

    /**
     * Character n-grams of a single token, padded with boundary markers.
     * Example: charNGrams("cat", 3) = [$ca, cat, at$].
     * A token shorter than n after padding becomes one n-gram, so short words are still indexed.
     */
    public static List<String> charNGrams(String token, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, got " + n);
        }
        String padded = BOUNDARY + token + BOUNDARY;
        if (padded.length() <= n) {
            return List.of(padded);
        }
        List<String> grams = new ArrayList<>(padded.length() - n + 1);
        for (int i = 0; i + n <= padded.length(); i++) {
            grams.add(padded.substring(i, i + n));
        }
        return grams;
    }

    public static Set<String> charNGramSet(String token, int n) {
        return new HashSet<>(charNGrams(token, n));
    }

    /**
     * Word-level shingles: every run of k consecutive tokens joined by a space.
     * Example with k=2: [the, quick, fox] gives {"the quick", "quick fox"}.
     * A document with fewer than k tokens becomes a single shingle.
     */
    public static Set<String> wordShingles(List<String> tokens, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("shingle size must be >= 1, got " + k);
        }
        Set<String> shingles = new HashSet<>();
        if (tokens.isEmpty()) {
            return shingles;
        }
        if (tokens.size() < k) {
            shingles.add(String.join(" ", tokens));
            return shingles;
        }
        for (int i = 0; i + k <= tokens.size(); i++) {
            shingles.add(String.join(" ", tokens.subList(i, i + k)));
        }
        return shingles;
    }

    /**
     * Compact signature: the sorted, distinct 64-bit FNV-1a hashes of the word shingles.
     * Jaccard on these arrays equals Jaccard on the shingle sets unless two different shingles
     * hash to the same 64-bit value, which is astronomically unlikely at these sizes.
     */
    public static long[] shingleSignature(List<String> tokens, int k) {
        Set<String> shingles = wordShingles(tokens, k);
        long[] hashes = new long[shingles.size()];
        int i = 0;
        for (String s : shingles) {
            hashes[i++] = fnv1a64(s);
        }
        java.util.Arrays.sort(hashes);
        return hashes;
    }

    static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
