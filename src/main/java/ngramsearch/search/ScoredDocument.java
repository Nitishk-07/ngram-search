package ngramsearch.search;

import java.util.List;

/**
 * One ranked document with every score component kept, so results can be explained.
 *
 * <p>score = ngramWeight * ngramScore + (1 - ngramWeight) * bm25Normalized
 */
public record ScoredDocument(
        int docId,
        String name,
        double score,
        double ngramScore,
        double bm25,
        double bm25Normalized,
        List<TermMatch> matches) {

    public String explain(double ngramWeight) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.3f = %.2f x ngram %.3f + %.2f x bm25 %.3f (raw %.3f)",
                score, ngramWeight, ngramScore, 1 - ngramWeight, bm25Normalized, bm25));
        if (!matches.isEmpty()) {
            sb.append(" | matched: ");
            for (int i = 0; i < matches.size(); i++) {
                TermMatch m = matches.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(m.exact() ? m.matchedTerm()
                        : String.format("%s->%s (%.2f)", m.queryTerm(), m.matchedTerm(), m.similarity()));
            }
        }
        return sb.toString();
    }
}
