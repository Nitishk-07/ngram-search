package ngramsearch.search;

import java.util.ArrayList;
import java.util.List;

import ngramsearch.index.InvertedIndex;
import ngramsearch.text.Similarity;

/**
 * Groups near-duplicate results with deterministic "leader" clustering.
 *
 * <p>Walk the results in rank order. Each result is compared to the representative (leader) of
 * every existing group, oldest group first. It joins the first group whose leader has shingle
 * Jaccard &gt;= threshold; otherwise it starts a new group and becomes its leader. Because results
 * arrive best-first, a group's leader is always its highest-ranked member.
 *
 * <p>Unlike single-link clustering this is not transitive: if A~B and B~C but not A~C, C is not
 * pulled into A's group through B. That avoids long chains of slightly-edited documents all
 * collapsing into one result.
 *
 * <p>Only the first topK groups are ever returned, so once topK groups exist a result is only
 * compared against those leaders: if it matches none of them it would start a group that is never
 * shown, and it is skipped. The output is identical to clustering everything and truncating, but
 * the work is bounded by window x topK comparisons instead of window x window.
 */
public final class ResultCollapser {

    private ResultCollapser() {
    }

    /**
     * @param ranked    results sorted best-first
     * @param threshold Jaccard at or above which a result is a near-duplicate of a leader
     * @param window    only the first {@code window} results are clustered; later ones are kept as
     *                  singletons (bounds the comparison cost)
     * @param topK      maximum number of groups to return
     */
    public static List<ResultGroup> collapse(List<ScoredDocument> ranked, InvertedIndex index, double threshold,
                                             int window, int topK) {
        List<ScoredDocument> leaders = new ArrayList<>();
        List<long[]> leaderSigs = new ArrayList<>();
        List<List<ResultGroup.Collapsed>> members = new ArrayList<>();

        int limit = Math.min(window, ranked.size());
        for (int i = 0; i < limit; i++) {
            ScoredDocument doc = ranked.get(i);
            long[] sig = index.signature(doc.docId());
            int joined = -1;
            double sim = 0;
            for (int g = 0; g < leaders.size(); g++) {
                sim = Similarity.jaccard(leaderSigs.get(g), sig);
                if (sim >= threshold) {
                    joined = g;
                    break;
                }
            }
            if (joined >= 0) {
                members.get(joined).add(new ResultGroup.Collapsed(doc, sim));
            } else if (leaders.size() < topK) {
                leaders.add(doc);
                leaderSigs.add(sig);
                members.add(new ArrayList<>());
            }
        }

        List<ResultGroup> groups = new ArrayList<>();
        for (int g = 0; g < leaders.size(); g++) {
            groups.add(new ResultGroup(leaders.get(g), List.copyOf(members.get(g))));
        }
        for (int i = limit; i < ranked.size() && groups.size() < topK; i++) {
            groups.add(new ResultGroup(ranked.get(i), List.of()));
        }
        return groups;
    }

    /** Collapsing disabled: every result is its own group. */
    public static List<ResultGroup> noCollapse(List<ScoredDocument> ranked, int topK) {
        List<ResultGroup> groups = new ArrayList<>();
        for (int i = 0; i < ranked.size() && i < topK; i++) {
            groups.add(new ResultGroup(ranked.get(i), List.of()));
        }
        return groups;
    }
}
