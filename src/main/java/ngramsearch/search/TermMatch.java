package ngramsearch.search;

/** A query word matched to a vocabulary word, e.g. "dijsktra" -> "dijkstra" with Dice 0.5. */
public record TermMatch(String queryTerm, String matchedTerm, double similarity) {

    public boolean exact() {
        return queryTerm.equals(matchedTerm);
    }
}
