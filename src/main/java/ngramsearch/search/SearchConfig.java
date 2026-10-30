package ngramsearch.search;

/**
 * Every tunable knob in one place. Use {@link #defaults()} and the {@code withX} methods.
 *
 * @param ngramWeight         weight of the n-gram (Dice) score in the final score; BM25 gets 1 - this
 * @param bm25K1              BM25 term-frequency saturation (typical 1.2 to 2.0)
 * @param bm25B               BM25 length normalization (0 = none, 1 = full)
 * @param minTermSimilarity   minimum Dice similarity for a vocabulary word to count as a match for a query word
 * @param minLengthRatio      shorter/longer length ratio a fuzzy match must have (stops "budjet" matching "jet")
 * @param maxExpansions       at most this many vocabulary words are matched per query word
 * @param expandKnownTerms    if false, a query word that exists in the vocabulary only matches itself
 *                            (stops "rover" also matching "over")
 * @param minCandidateOverlap fraction of the query's n-grams a document must contain to be scored at all
 * @param collapseEnabled     whether near-duplicate results are grouped
 * @param collapseThreshold   shingle Jaccard at or above which two results are near-duplicates
 * @param collapseWindow      only the top this-many ranked results are considered for collapsing
 * @param topK                number of result groups returned
 */
public record SearchConfig(
        double ngramWeight,
        double bm25K1,
        double bm25B,
        double minTermSimilarity,
        double minLengthRatio,
        int maxExpansions,
        boolean expandKnownTerms,
        double minCandidateOverlap,
        boolean collapseEnabled,
        double collapseThreshold,
        int collapseWindow,
        int topK) {

    public SearchConfig {
        requireRange("ngramWeight", ngramWeight, 0, 1);
        requireRange("bm25B", bm25B, 0, 1);
        requireRange("minTermSimilarity", minTermSimilarity, 0, 1);
        requireRange("minLengthRatio", minLengthRatio, 0, 1);
        requireRange("minCandidateOverlap", minCandidateOverlap, 0, 1);
        requireRange("collapseThreshold", collapseThreshold, 0, 1);
        if (bm25K1 < 0) {
            throw new IllegalArgumentException("bm25K1 must be >= 0");
        }
        if (maxExpansions < 1 || collapseWindow < 1 || topK < 1) {
            throw new IllegalArgumentException("maxExpansions, collapseWindow and topK must be >= 1");
        }
    }

    public static SearchConfig defaults() {
        return new SearchConfig(0.5, 1.2, 0.75, 0.4, 0.6, 5, false, 0.2, true, 0.5, 100, 10);
    }

    public SearchConfig withNgramWeight(double w) {
        return new SearchConfig(w, bm25K1, bm25B, minTermSimilarity, minLengthRatio, maxExpansions,
                expandKnownTerms, minCandidateOverlap, collapseEnabled, collapseThreshold, collapseWindow, topK);
    }

    public SearchConfig withMinTermSimilarity(double s) {
        return new SearchConfig(ngramWeight, bm25K1, bm25B, s, minLengthRatio, maxExpansions,
                expandKnownTerms, minCandidateOverlap, collapseEnabled, collapseThreshold, collapseWindow, topK);
    }

    public SearchConfig withExpandKnownTerms(boolean expand) {
        return new SearchConfig(ngramWeight, bm25K1, bm25B, minTermSimilarity, minLengthRatio, maxExpansions,
                expand, minCandidateOverlap, collapseEnabled, collapseThreshold, collapseWindow, topK);
    }

    public SearchConfig withMinLengthRatio(double r) {
        return new SearchConfig(ngramWeight, bm25K1, bm25B, minTermSimilarity, r, maxExpansions,
                expandKnownTerms, minCandidateOverlap, collapseEnabled, collapseThreshold, collapseWindow, topK);
    }

    public SearchConfig withCollapse(boolean enabled) {
        return new SearchConfig(ngramWeight, bm25K1, bm25B, minTermSimilarity, minLengthRatio, maxExpansions,
                expandKnownTerms, minCandidateOverlap, enabled, collapseThreshold, collapseWindow, topK);
    }

    public SearchConfig withCollapseThreshold(double t) {
        return new SearchConfig(ngramWeight, bm25K1, bm25B, minTermSimilarity, minLengthRatio, maxExpansions,
                expandKnownTerms, minCandidateOverlap, collapseEnabled, t, collapseWindow, topK);
    }

    public SearchConfig withTopK(int k) {
        return new SearchConfig(ngramWeight, bm25K1, bm25B, minTermSimilarity, minLengthRatio, maxExpansions,
                expandKnownTerms, minCandidateOverlap, collapseEnabled, collapseThreshold, collapseWindow, k);
    }

    private static void requireRange(String name, double v, double lo, double hi) {
        if (Double.isNaN(v) || v < lo || v > hi) {
            throw new IllegalArgumentException(name + " must be in [" + lo + ", " + hi + "], got " + v);
        }
    }
}
