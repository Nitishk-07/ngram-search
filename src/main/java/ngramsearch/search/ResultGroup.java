package ngramsearch.search;

import java.util.List;

/**
 * A representative result plus the near-duplicates collapsed under it.
 * When collapsing is disabled every group has an empty {@code collapsed} list.
 */
public record ResultGroup(ScoredDocument representative, List<Collapsed> collapsed) {

    /** A hidden near-duplicate and its shingle Jaccard similarity to the representative. */
    public record Collapsed(ScoredDocument doc, double similarity) {
    }

    public int collapsedCount() {
        return collapsed.size();
    }

    public List<Integer> collapsedIds() {
        return collapsed.stream().map(c -> c.doc().docId()).toList();
    }
}
