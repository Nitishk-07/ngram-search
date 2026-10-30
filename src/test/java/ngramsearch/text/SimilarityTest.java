package ngramsearch.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SimilarityTest {

    @Test
    void jaccardKnownValues() {
        assertEquals(1.0, Similarity.jaccard(Set.of("a", "b"), Set.of("a", "b")));
        assertEquals(0.0, Similarity.jaccard(Set.of("a"), Set.of("b")));
        assertEquals(0.5, Similarity.jaccard(Set.of("a", "b", "c"), Set.of("a", "b", "c", "d", "e", "f")));
        assertEquals(1.0 / 3, Similarity.jaccard(Set.of("a", "b"), Set.of("b", "c")), 1e-12);
    }

    @Test
    void diceKnownValues() {
        assertEquals(0.5, Similarity.dice(Set.of("a", "b"), Set.of("b", "c")), 1e-12);
        assertEquals(2.0 * 3 / 9, Similarity.dice(3, 4, 5), 1e-12);
    }

    @Test
    void diceIsAlwaysAtLeastJaccard() {
        Set<String> a = Set.of("x", "y", "z", "w");
        Set<String> b = Set.of("y", "z", "q");
        double j = Similarity.jaccard(a, b);
        assertEquals(2 * j / (1 + j), Similarity.dice(a, b), 1e-12);   // D = 2J / (1 + J)
    }

    @Test
    void sortedArrayJaccardMatchesSetJaccard() {
        assertEquals(0.5, Similarity.jaccard(new long[] {1, 2, 3}, new long[] {1, 2, 3, 4, 5, 6}));
        assertEquals(1.0 / 3, Similarity.jaccard(new long[] {-5, 7}, new long[] {7, 9}), 1e-12);
        assertEquals(0.0, Similarity.jaccard(new long[] {1}, new long[] {2}));
        assertEquals(1.0, Similarity.jaccard(new long[0], new long[0]));
    }

    @Test
    void shingleSignatureJaccardEqualsShingleSetJaccard() {
        java.util.List<String> a = TextNormalizer.tokens("the quick brown fox jumps over the lazy dog today");
        java.util.List<String> b = TextNormalizer.tokens("the quick brown fox leaps over the lazy dog today");
        double viaSets = Similarity.jaccard(NGrams.wordShingles(a, 3), NGrams.wordShingles(b, 3));
        double viaSigs = Similarity.jaccard(NGrams.shingleSignature(a, 3), NGrams.shingleSignature(b, 3));
        assertEquals(viaSets, viaSigs, 1e-12);
    }

    @Test
    void emptySets() {
        assertEquals(1.0, Similarity.jaccard(Set.of(), Set.of()));
        assertEquals(0.0, Similarity.jaccard(Set.of(), Set.of("a")));
        assertEquals(1.0, Similarity.dice(Set.of(), Set.of()));
        assertEquals(0.0, Similarity.dice(Set.of("a"), Set.of()));
    }
}
