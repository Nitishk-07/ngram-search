package ngramsearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ngramsearch.index.Document;
import ngramsearch.index.InvertedIndex;

class SearchEngineTest {

    private static final List<Document> DOCS = List.of(
            new Document("dijkstra.txt", "Dijkstra's algorithm finds the shortest path in a weighted graph. "
                    + "It uses a priority queue and runs in O((V + E) log V) time."),
            new Document("bfs.txt", "Breadth first search explores a graph level by level and finds the shortest path "
                    + "when every edge has the same weight."),
            new Document("pasta.txt", "Boil the pasta in salted water, then toss it with garlic, olive oil and basil."),
            new Document("coffee.txt", "Espresso is brewed by forcing hot water through finely ground coffee beans."),
            new Document("sorting.txt", "Merge sort and quicksort are comparison sorting algorithms that run in "
                    + "O(n log n) time on average."));

    private static SearchEngine engine(SearchConfig config) {
        return new SearchEngine(InvertedIndex.build(DOCS, 3, 3), config);
    }

    private static List<String> names(SearchResponse r) {
        return r.groups().stream().map(g -> g.representative().name()).toList();
    }

    @Test
    void exactMatchRanksFirst() {
        SearchResponse r = engine(SearchConfig.defaults()).search("espresso");
        assertEquals("coffee.txt", names(r).get(0));
        ScoredDocument top = r.groups().get(0).representative();
        assertEquals(1.0, top.ngramScore(), 1e-12);
        assertTrue(top.matches().get(0).exact());
    }

    @Test
    void multiWordQueryPrefersDocumentMatchingMoreWords() {
        SearchResponse r = engine(SearchConfig.defaults()).search("shortest path priority queue");
        assertEquals(List.of("dijkstra.txt", "bfs.txt"), names(r).subList(0, 2));
    }

    @Test
    void typoTolerantRetrieval() {
        SearchEngine e = engine(SearchConfig.defaults());
        // transposition, deletion, substitution, insertion
        assertEquals("dijkstra.txt", names(e.search("dijsktra")).get(0));
        assertEquals("coffee.txt", names(e.search("espreso")).get(0));
        assertEquals("pasta.txt", names(e.search("garlik")).get(0));
        assertEquals("sorting.txt", names(e.search("quicksoort")).get(0));
    }

    @Test
    void typoMatchesAreReportedWithSimilarity() {
        ScoredDocument top = engine(SearchConfig.defaults()).search("dijsktra").groups().get(0).representative();
        TermMatch m = top.matches().get(0);
        assertEquals("dijsktra", m.queryTerm());
        assertEquals("dijkstra", m.matchedTerm());   // "Dijkstra's" normalizes to "dijkstra"
        assertFalse(m.exact());
        assertTrue(m.similarity() >= 0.4 && m.similarity() < 1.0);
    }

    @Test
    void unrelatedQueryReturnsNothing() {
        SearchEngine e = engine(SearchConfig.defaults());
        assertTrue(e.search("xylophone").isEmpty());
        assertTrue(e.search("kangaroo jukebox").isEmpty());
    }

    @Test
    void emptyAndStopwordOnlyQueries() {
        SearchEngine e = engine(SearchConfig.defaults());
        assertTrue(e.search("").isEmpty());
        assertTrue(e.search("   ").isEmpty());
        assertTrue(e.search("?!.,").isEmpty());
        assertTrue(e.search("the and of").isEmpty());
    }

    @Test
    void emptyIndexReturnsNothing() {
        SearchEngine e = new SearchEngine(InvertedIndex.build(List.of(), 3, 3), SearchConfig.defaults());
        assertTrue(e.search("anything").isEmpty());
    }

    @Test
    void scoreIsTheDocumentedWeightedSum() {
        for (double w : new double[] {0.0, 0.3, 0.5, 1.0}) {
            SearchResponse r = engine(SearchConfig.defaults().withNgramWeight(w)).search("graph shortest path");
            for (ResultGroup g : r.groups()) {
                ScoredDocument d = g.representative();
                assertEquals(w * d.ngramScore() + (1 - w) * d.bm25Normalized(), d.score(), 1e-12);
                assertTrue(d.bm25Normalized() >= 0 && d.bm25Normalized() <= 1);
            }
        }
    }

    @Test
    void bestBm25IsNormalizedToOne() {
        SearchResponse r = engine(SearchConfig.defaults()).search("graph");
        double max = r.groups().stream().mapToDouble(g -> g.representative().bm25Normalized()).max().orElse(0);
        assertEquals(1.0, max, 1e-12);
    }

    @Test
    void rankingIsDeterministicAndTiesBreakByName() {
        List<Document> twins = List.of(
                new Document("b.txt", "identical words here"),
                new Document("a.txt", "identical words here"),
                new Document("c.txt", "identical words here"));
        SearchEngine e = new SearchEngine(InvertedIndex.build(twins, 3, 3), SearchConfig.defaults().withCollapse(false));
        SearchResponse first = e.search("identical");
        assertEquals(List.of("a.txt", "b.txt", "c.txt"), names(first));
        for (int i = 0; i < 20; i++) {
            assertEquals(names(first), names(e.search("identical")));
        }
    }

    @Test
    void topKLimitsResults() {
        SearchResponse r = engine(SearchConfig.defaults().withTopK(1)).search("graph");
        assertEquals(1, r.groups().size());
        assertTrue(r.matchedDocs() >= 2);
    }

    @Test
    void higherSimilarityThresholdDropsTypoMatches() {
        SearchEngine strict = engine(SearchConfig.defaults().withMinTermSimilarity(0.9));
        assertTrue(strict.search("dijsktra").isEmpty());
        assertFalse(strict.search("dijkstra").isEmpty());
    }

    private static final List<Document> ROVER = List.of(
            new Document("rover.txt", "The rover drove across the crater."),
            new Document("over.txt", "Pour hot water over the grounds."),
            new Document("jet.txt", "The jet landed at the airport."),
            new Document("budget.txt", "The council approved the budget."));

    @Test
    void knownWordIsNotFuzzyMatchedToOtherWords() {
        // regression: "rover" used to also match "over" (Dice 0.67) and pull in the coffee doc
        SearchEngine e = new SearchEngine(InvertedIndex.build(ROVER, 3, 3), SearchConfig.defaults());
        assertEquals(List.of("rover.txt"), names(e.search("rover")));

        SearchEngine loose = new SearchEngine(InvertedIndex.build(ROVER, 3, 3),
                SearchConfig.defaults().withExpandKnownTerms(true));
        assertEquals(List.of("rover.txt", "over.txt"), names(loose.search("rover")));
    }

    @Test
    void lengthRatioBlocksShortWordMatches() {
        // regression: "budjet" used to match "jet" (Dice 0.44) as well as "budget"
        SearchEngine e = new SearchEngine(InvertedIndex.build(ROVER, 3, 3), SearchConfig.defaults());
        assertEquals(List.of("budget.txt"), names(e.search("budjet")));

        SearchEngine noRatio = new SearchEngine(InvertedIndex.build(ROVER, 3, 3),
                SearchConfig.defaults().withMinLengthRatio(0));
        assertTrue(names(noRatio.search("budjet")).contains("jet.txt"));
    }

    @Test
    void invalidConfigIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SearchConfig.defaults().withNgramWeight(1.5));
        assertThrows(IllegalArgumentException.class, () -> SearchConfig.defaults().withCollapseThreshold(-0.1));
        assertThrows(IllegalArgumentException.class, () -> SearchConfig.defaults().withTopK(0));
    }
}
