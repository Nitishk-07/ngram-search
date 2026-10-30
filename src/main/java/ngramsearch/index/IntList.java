package ngramsearch.index;

import java.util.Arrays;

/**
 * Growable int array. Posting lists hold millions of doc ids on big corpora, and a
 * List&lt;Integer&gt; would box every one of them (about 4x the memory).
 */
final class IntList {
    private int[] data = new int[4];
    private int size;

    void add(int value) {
        if (size == data.length) {
            data = Arrays.copyOf(data, size * 2);
        }
        data[size++] = value;
    }

    /** Doc ids are added in increasing order, so this skips adding the same doc twice in a row. */
    void addIfNotLast(int value) {
        if (size == 0 || data[size - 1] != value) {
            add(value);
        }
    }

    int[] toArray() {
        return Arrays.copyOf(data, size);
    }
}
