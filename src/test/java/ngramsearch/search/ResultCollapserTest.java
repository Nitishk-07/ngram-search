package ngramsearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ngramsearch.index.Document;
import ngramsearch.index.InvertedIndex;

class ResultCollapserTest {

    private static final String ARTICLE = "The city council approved a new budget on Tuesday that adds funding for "
            + "public transit, road repairs and three new libraries. The mayor said the plan keeps property taxes "
            + "flat while expanding bus service to the northern neighborhoods by next spring.";

    // same story with a handful of words changed, like a syndicated repost
    private static final String REPOST = "The city council passed a new budget on Tuesday that adds funding for "
            + "public transit, road repairs and three new libraries. The mayor said the plan keeps property taxes "
            + "flat while expanding bus service to the northern neighborhoods by early spring.";

    private static final String OTHER = "A local bakery won a regional award for its sourdough bread, which the "
            + "council member described as the best in the state during a visit on Tuesday.";

    private static SearchEngine engine(List<Document> docs, int shingle, SearchConfig config) {
        return new SearchEngine(InvertedIndex.build(docs, 3, shingle), config);
    }

    @Test
    void nearDuplicatesCollapseUnderHighestRankedResult() {
        List<Document> docs = List.of(
                new Document("original.txt", ARTICLE),
                new Document("repost.txt", REPOST),
                new Document("bakery.txt", OTHER));
        SearchResponse r = engine(docs, 3, SearchConfig.defaults()).search("council tuesday");

        assertEquals(2, r.groups().size());
        ResultGroup budget = r.groups().stream()
                .filter(g -> g.collapsedCount() == 1).findFirst().orElseThrow();
        List<String> pair = List.of(budget.representative().name(), budget.collapsed().get(0).doc().name());
        assertTrue(pair.containsAll(List.of("original.txt", "repost.txt")));
        assertTrue(budget.representative().score() >= budget.collapsed().get(0).doc().score());
        assertTrue(budget.collapsed().get(0).similarity() >= 0.5);
    }

    @Test
    void collapsedIdsAndCountArePreserved() {
        List<Document> docs = List.of(
                new Document("a.txt", ARTICLE),
                new Document("b.txt", REPOST),
                new Document("c.txt", ARTICLE));
        SearchResponse r = engine(docs, 3, SearchConfig.defaults()).search("budget");
        assertEquals(1, r.groups().size());
        ResultGroup g = r.groups().get(0);
        assertEquals(2, g.collapsedCount());
        List<Integer> all = new java.util.ArrayList<>(g.collapsedIds());
        all.add(g.representative().docId());
        assertEquals(List.of(0, 1, 2), all.stream().sorted().toList());
    }

    @Test
    void disablingCollapseReturnsEveryDocument() {
        List<Document> docs = List.of(new Document("a.txt", ARTICLE), new Document("b.txt", REPOST));
        SearchResponse r = engine(docs, 3, SearchConfig.defaults().withCollapse(false)).search("budget");
        assertEquals(2, r.groups().size());
        assertTrue(r.groups().stream().allMatch(g -> g.collapsedCount() == 0));
    }

    /**
     * With shingle size 1 the signatures are just word sets:
     * {apple, banana, cherry} vs {apple, banana, cherry, durian, elderberry, feijoa}
     * gives Jaccard exactly 3 / 6 = 0.5.
     */
    private static final List<Document> HALF = List.of(
            new Document("short.txt", "apple banana cherry"),
            new Document("long.txt", "apple banana cherry durian elderberry feijoa"));

    @Test
    void thresholdIsInclusive() {
        SearchResponse r = engine(HALF, 1, SearchConfig.defaults().withCollapseThreshold(0.5)).search("apple");
        assertEquals(1, r.groups().size());
        assertEquals(0.5, r.groups().get(0).collapsed().get(0).similarity(), 1e-12);
    }

    @Test
    void justAboveThresholdDoesNotCollapse() {
        SearchResponse r = engine(HALF, 1, SearchConfig.defaults().withCollapseThreshold(0.5000001)).search("apple");
        assertEquals(2, r.groups().size());
    }

    @Test
    void thresholdZeroCollapsesEverythingAndOneOnlyIdentical() {
        List<Document> docs = List.of(
                new Document("x.txt", "apple banana"),
                new Document("y.txt", "apple cherry"),
                new Document("z.txt", "apple banana"));
        assertEquals(1, engine(docs, 1, SearchConfig.defaults().withCollapseThreshold(0.0))
                .search("apple").groups().size());
        SearchResponse exact = engine(docs, 1, SearchConfig.defaults().withCollapseThreshold(1.0)).search("apple");
        assertEquals(2, exact.groups().size());
    }

    @Test
    void leaderClusteringIsNotTransitive() {
        // word sets: A~B J=4/7=0.57, B~C J=5/7=0.71, but A~C only J=3/8=0.375.
        // C is close to B, yet B is a member (not the leader) of A's group, so C starts its own group.
        List<Document> docs = List.of(
                new Document("a.txt", "apple apple apple w1 w2 w3 w4"),
                new Document("b.txt", "apple apple w2 w3 w4 w5 w6"),
                new Document("c.txt", "apple w2 w4 w5 w6 w7"));
        InvertedIndex idx = InvertedIndex.build(docs, 3, 1);
        List<ScoredDocument> ranked = List.of(
                new ScoredDocument(0, "a.txt", 0.9, 1, 1, 1, List.of()),
                new ScoredDocument(1, "b.txt", 0.8, 1, 1, 1, List.of()),
                new ScoredDocument(2, "c.txt", 0.7, 1, 1, 1, List.of()));
        List<ResultGroup> groups = ResultCollapser.collapse(ranked, idx, 0.55, 100, 10);
        assertEquals(2, groups.size());
        assertEquals("a.txt", groups.get(0).representative().name());
        assertEquals(List.of(1), groups.get(0).collapsedIds());
        assertEquals("c.txt", groups.get(1).representative().name());
    }

    @Test
    void resultsOutsideWindowAreNotCollapsed() {
        List<Document> docs = List.of(new Document("a.txt", ARTICLE), new Document("b.txt", ARTICLE));
        InvertedIndex idx = InvertedIndex.build(docs, 3, 3);
        List<ScoredDocument> ranked = List.of(
                new ScoredDocument(0, "a.txt", 0.9, 1, 1, 1, List.of()),
                new ScoredDocument(1, "b.txt", 0.8, 1, 1, 1, List.of()));
        assertEquals(1, ResultCollapser.collapse(ranked, idx, 0.5, 2, 10).size());
        assertEquals(2, ResultCollapser.collapse(ranked, idx, 0.5, 1, 10).size());
    }

    @Test
    void stoppingAtTopKGroupsGivesSameAnswerAsFullClustering() {
        // 40 docs built from 8 base texts with small edits, so there are many duplicate clusters
        java.util.Random rng = new java.util.Random(7);
        String[] words = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima".split(" ");
        List<Document> docs = new java.util.ArrayList<>();
        List<String[]> bases = new java.util.ArrayList<>();
        for (int b = 0; b < 8; b++) {
            String[] base = new String[30];
            for (int w = 0; w < base.length; w++) {
                base[w] = words[rng.nextInt(words.length)];
            }
            bases.add(base);
        }
        for (int i = 0; i < 40; i++) {
            String[] copy = bases.get(rng.nextInt(bases.size())).clone();
            copy[rng.nextInt(copy.length)] = words[rng.nextInt(words.length)];
            docs.add(new Document(String.format("d%02d.txt", i), String.join(" ", copy)));
        }
        InvertedIndex idx = InvertedIndex.build(docs, 3, 3);
        List<ScoredDocument> ranked = new java.util.ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            ranked.add(new ScoredDocument(i, docs.get(i).name(), 1.0 - i / 100.0, 1, 1, 1, List.of()));
        }
        List<ResultGroup> full = ResultCollapser.collapse(ranked, idx, 0.5, 100, 1000);
        for (int k = 1; k <= full.size(); k++) {
            assertEquals(full.subList(0, k), ResultCollapser.collapse(ranked, idx, 0.5, 100, k));
        }
    }

    @Test
    void emptyInput() {
        InvertedIndex idx = InvertedIndex.build(List.of(), 3, 3);
        assertTrue(ResultCollapser.collapse(List.of(), idx, 0.5, 100, 10).isEmpty());
        assertTrue(ResultCollapser.noCollapse(List.of(), 10).isEmpty());
    }
}
