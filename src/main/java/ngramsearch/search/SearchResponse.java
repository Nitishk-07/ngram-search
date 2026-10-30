package ngramsearch.search;

import java.util.List;

/**
 * @param groups         ranked result groups (at most topK)
 * @param candidates     documents that passed the n-gram overlap filter
 * @param matchedDocs    candidates with a non-zero score
 * @param elapsedNanos   wall-clock time spent inside {@link SearchEngine#search}
 */
public record SearchResponse(List<ResultGroup> groups, int candidates, int matchedDocs, long elapsedNanos) {

    public static SearchResponse empty(long elapsedNanos) {
        return new SearchResponse(List.of(), 0, 0, elapsedNanos);
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }
}
