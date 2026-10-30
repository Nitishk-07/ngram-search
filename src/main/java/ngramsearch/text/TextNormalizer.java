package ngramsearch.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns raw text into a clean, comparable form.
 *
 * <p>Steps: Unicode NFKD decomposition (so "café" becomes "cafe" + accent mark), strip the
 * accent marks, lowercase, drop possessive 's ("Dijkstra's" becomes "dijkstra"), delete other
 * apostrophes so "don't" stays one token, then replace every
 * run of non letter/digit characters with a single space.
 */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern POSSESSIVE = Pattern.compile("(\\p{L})['’]s\\b");
    private static final Pattern APOSTROPHES = Pattern.compile("['’]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    /** Small English stopword list. These words appear everywhere and carry no search signal. */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "has", "have",
            "he", "her", "his", "i", "in", "is", "it", "its", "of", "on", "or", "our", "she",
            "so", "that", "the", "their", "them", "they", "this", "to", "was", "we", "were",
            "what", "when", "which", "who", "will", "with", "you", "your");

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFKD);
        s = MARKS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        s = POSSESSIVE.matcher(s).replaceAll("$1");
        s = APOSTROPHES.matcher(s).replaceAll("");
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        return s.trim();
    }

    /** All tokens, in order, including stopwords. */
    public static List<String> tokens(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split(" "));
    }

    /** Tokens with stopwords removed. These are the words we index and score. */
    public static List<String> contentTokens(String text) {
        List<String> out = new ArrayList<>();
        for (String t : tokens(text)) {
            if (!isStopword(t)) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean isStopword(String token) {
        return STOPWORDS.contains(token);
    }
}
