package ngramsearch.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvertedIndexTest {

    private static final List<Document> DOCS = List.of(
            new Document("cats.txt", "The cat sat on the mat. Cats are great."),
            new Document("dogs.txt", "A dog chased the cat around the yard."),
            new Document("birds.txt", "Birds sing at dawn."));

    @Test
    void ngramPostingsListDocsContainingEachGram() {
        InvertedIndex idx = InvertedIndex.build(DOCS, 3, 3);
        assertArrayEquals(new int[] {0, 1}, idx.docsWithNgram("$ca"));   // cat, cats (and not "chased", which starts with $ch)
        assertArrayEquals(new int[] {1}, idx.docsWithNgram("dog"));
        assertArrayEquals(new int[0], idx.docsWithNgram("zzz"));
    }

    @Test
    void stopwordsAreNotIndexed() {
        InvertedIndex idx = InvertedIndex.build(DOCS, 3, 3);
        assertFalse(idx.containsTerm("the"));
        assertTrue(idx.containsTerm("cat"));
        // "the" is gone, so "$th" should only come from real words (none here)
        assertArrayEquals(new int[0], idx.docsWithNgram("$th"));
    }

    @Test
    void termStatistics() {
        InvertedIndex idx = InvertedIndex.build(DOCS, 3, 3);
        assertEquals(2, idx.documentFrequency("cat"));
        assertEquals(1, idx.termFrequency("cat", 0));
        assertEquals(1, idx.termFrequency("cats", 0));
        assertEquals(0, idx.termFrequency("cat", 2));
        // cats.txt content words: cat sat mat cats great = 5
        assertEquals(5, idx.docLength(0));
        assertEquals((5 + 5 + 3) / 3.0, idx.avgDocLength(), 1e-12);
    }

    @Test
    void ngramToTermIndexSupportsFuzzyLookup() {
        InvertedIndex idx = InvertedIndex.build(DOCS, 3, 3);
        assertEquals(List.of("cat", "cats"), idx.termsWithNgram("$ca"));
        assertEquals(3, idx.termNgramCount("cat"));
    }

    @Test
    void ngramSizeIsConfigurable() {
        InvertedIndex bi = InvertedIndex.build(DOCS, 2, 3);
        InvertedIndex four = InvertedIndex.build(DOCS, 4, 3);
        assertEquals(2, bi.n());
        assertTrue(bi.docsWithNgram("ca").length > 0);
        assertTrue(four.docsWithNgram("$cat").length > 0);
        assertEquals(0, four.docsWithNgram("$ca").length);
    }

    @Test
    void emptyCorpus() {
        InvertedIndex idx = InvertedIndex.build(List.of(), 3, 3);
        assertEquals(0, idx.numDocs());
        assertEquals(0, idx.vocabularySize());
        assertEquals(0.0, idx.avgDocLength());
    }

    @Test
    void emptyDocumentIsIndexedWithoutTerms() {
        InvertedIndex idx = InvertedIndex.build(List.of(new Document("empty.txt", "")), 3, 3);
        assertEquals(1, idx.numDocs());
        assertEquals(0, idx.docLength(0));
        assertEquals(0, idx.signature(0).length);
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> InvertedIndex.build(DOCS, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> InvertedIndex.build(DOCS, 3, 0));
    }

    @Test
    void loaderReadsTxtFilesSortedAndSkipsOthers(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("b.txt"), "bravo");
        Files.writeString(dir.resolve("a.txt"), "alpha");
        Files.writeString(dir.resolve("notes.md"), "ignored");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/c.txt"), "charlie");

        List<Document> docs = DocumentLoader.loadDirectory(dir);
        assertEquals(List.of("a.txt", "b.txt", "sub/c.txt"), docs.stream().map(Document::name).toList());
        assertEquals("alpha", docs.get(0).text());
    }

    @Test
    void loaderRejectsMissingDirectory(@TempDir Path dir) {
        assertThrows(IOException.class, () -> DocumentLoader.loadDirectory(dir.resolve("nope")));
    }
}
