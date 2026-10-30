package ngramsearch.index;

/** A document to index. Its id is its position in the list passed to {@link InvertedIndex#build}. */
public record Document(String name, String text) {
    public Document {
        if (name == null || text == null) {
            throw new IllegalArgumentException("name and text must not be null");
        }
    }
}
