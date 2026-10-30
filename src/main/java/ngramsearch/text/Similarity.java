package ngramsearch.text;

import java.util.Set;

/** Set similarity measures. */
public final class Similarity {

    private Similarity() {
    }

    /** |A ∩ B| / |A ∪ B|. Two empty sets are treated as identical (1.0). */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int shared = intersectionSize(a, b);
        return (double) shared / (a.size() + b.size() - shared);
    }

    /** 2|A ∩ B| / (|A| + |B|). Two empty sets are treated as identical (1.0). */
    public static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        return dice(intersectionSize(a, b), a.size(), b.size());
    }

    /** Dice from precomputed counts, used when the intersection was counted via an index. */
    public static double dice(int shared, int sizeA, int sizeB) {
        if (sizeA + sizeB == 0) {
            return 1.0;
        }
        return 2.0 * shared / (sizeA + sizeB);
    }

    /**
     * Jaccard of two sorted arrays of distinct values, by a two-pointer merge in O(|A| + |B|)
     * with no hashing or allocation.
     */
    public static double jaccard(long[] a, long[] b) {
        if (a.length == 0 && b.length == 0) {
            return 1.0;
        }
        int i = 0;
        int j = 0;
        int shared = 0;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                shared++;
                i++;
                j++;
            } else if (a[i] < b[j]) {
                i++;
            } else {
                j++;
            }
        }
        return (double) shared / (a.length + b.length - shared);
    }

    public static int intersectionSize(Set<String> a, Set<String> b) {
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = small == a ? b : a;
        int count = 0;
        for (String s : small) {
            if (large.contains(s)) {
                count++;
            }
        }
        return count;
    }
}
