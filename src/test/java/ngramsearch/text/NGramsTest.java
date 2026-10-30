package ngramsearch.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class NGramsTest {

    @Test
    void trigramsArePaddedWithBoundaryMarkers() {
        assertEquals(List.of("$ca", "cat", "at$"), NGrams.charNGrams("cat", 3));
    }

    @Test
    void bigramsAndFourGrams() {
        assertEquals(List.of("$c", "ca", "at", "t$"), NGrams.charNGrams("cat", 2));
        assertEquals(List.of("$cat", "cat$"), NGrams.charNGrams("cat", 4));
    }

    @Test
    void shortTokenBecomesSingleGram() {
        assertEquals(List.of("$a$"), NGrams.charNGrams("a", 3));
        assertEquals(List.of("$ab$"), NGrams.charNGrams("ab", 5));
    }

    @Test
    void repeatedGramsAreDeduplicatedInSet() {
        // "aaaa" -> $aa, aaa, aaa, aa$
        assertEquals(4, NGrams.charNGrams("aaaa", 3).size());
        assertEquals(Set.of("$aa", "aaa", "aa$"), NGrams.charNGramSet("aaaa", 3));
    }

    @Test
    void invalidNIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> NGrams.charNGrams("cat", 0));
    }

    @Test
    void wordShingles() {
        assertEquals(Set.of("a b", "b c", "c d"), NGrams.wordShingles(List.of("a", "b", "c", "d"), 2));
        assertEquals(Set.of("a b"), NGrams.wordShingles(List.of("a", "b"), 5));
        assertTrue(NGrams.wordShingles(List.of(), 3).isEmpty());
    }

    @Test
    void oneTypoKeepsMostTrigrams() {
        Set<String> right = NGrams.charNGramSet("algorithm", 3);
        Set<String> typo = NGrams.charNGramSet("algoritm", 3);
        assertTrue(Similarity.dice(right, typo) > 0.6);
    }
}
