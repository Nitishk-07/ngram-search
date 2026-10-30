package ngramsearch.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void lowercasesAndReplacesPunctuationWithSpaces() {
        assertEquals("hello world 42", TextNormalizer.normalize("Hello, WORLD!!  42."));
    }

    @Test
    void collapsesWhitespaceAndTrims() {
        assertEquals("a b c", TextNormalizer.normalize("  a\t\tb\n\nc  "));
    }

    @Test
    void stripsAccents() {
        assertEquals("cafe naive resume", TextNormalizer.normalize("Café naïve résumé"));
    }

    @Test
    void apostrophesDoNotSplitWords() {
        assertEquals(List.of("dont", "students"), TextNormalizer.tokens("Don't students'"));
    }

    @Test
    void possessiveSIsDropped() {
        assertEquals(List.of("dijkstra", "algorithm"), TextNormalizer.tokens("Dijkstra's algorithm"));
        assertEquals(List.of("alice", "book"), TextNormalizer.tokens("Alice’s book"));
        assertEquals(List.of("dont", "stop"), TextNormalizer.tokens("don't stop"));   // not a possessive
    }

    @Test
    void hyphensAndUnderscoresSplitWords() {
        assertEquals(List.of("state", "of", "the", "art", "snake", "case"),
                TextNormalizer.tokens("state-of-the-art snake_case"));
    }

    @Test
    void emptyAndNullInput() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize(""));
        assertEquals(List.of(), TextNormalizer.tokens("   ...!!!  "));
        assertTrue(TextNormalizer.contentTokens("").isEmpty());
    }

    @Test
    void contentTokensDropStopwords() {
        assertEquals(List.of("quick", "fox", "jumps"), TextNormalizer.contentTokens("The quick fox jumps and the"));
        assertTrue(TextNormalizer.isStopword("the"));
        assertFalse(TextNormalizer.isStopword("fox"));
    }
}
