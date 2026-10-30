package ngramsearch.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ngramsearch.index.InvertedIndex;
import ngramsearch.text.NGrams;
import ngramsearch.text.Similarity;
import ngramsearch.text.TextNormalizer;

/**
 * Query pipeline:
 * <ol>
 *   <li>Normalize the query and drop stopwords.</li>
 *   <li>Fuzzy term matching: a query word found in the vocabulary matches only itself (unless
 *       expandKnownTerms). Otherwise find vocabulary words of similar length whose character
 *       n-gram sets have Dice similarity &gt;= minTermSimilarity (via the ngram -&gt; terms index).</li>
 *   <li>Candidate retrieval: count, per document, how many of the query's n-grams it contains
 *       (via the ngram -&gt; docs index). Keep documents above minCandidateOverlap.</li>
 *   <li>Score each candidate: an n-gram score (average best Dice per query word) and a BM25 score
 *       over the matched words, BM25 min-max normalized to [0, 1] within the result set.</li>
 *   <li>Rank by the weighted sum, break ties by document name, optionally collapse near-duplicates.</li>
 * </ol>
 */
public final class SearchEngine {

    private static final Comparator<ScoredDocument> RANKING = Comparator
            .comparingDouble(ScoredDocument::score).reversed()
            .thenComparing(ScoredDocument::name)
            .thenComparingInt(ScoredDocument::docId);

    private final InvertedIndex index;
    private final SearchConfig config;

    public SearchEngine(InvertedIndex index, SearchConfig config) {
        this.index = index;
        this.config = config;
    }

    public InvertedIndex index() {
        return index;
    }

    public SearchConfig config() {
        return config;
    }

    public SearchResponse search(String query) {
        long start = System.nanoTime();
        List<String> queryTerms = new ArrayList<>(new LinkedHashSet<>(TextNormalizer.contentTokens(query)));
        if (queryTerms.isEmpty() || index.numDocs() == 0) {
            return SearchResponse.empty(System.nanoTime() - start);
        }

        // 1) fuzzy term matching
        Map<String, List<TermMatch>> expansions = new HashMap<>();
        boolean anyMatch = false;
        for (String q : queryTerms) {
            List<TermMatch> matches = expand(q);
            expansions.put(q, matches);
            anyMatch |= !matches.isEmpty();
        }
        if (!anyMatch) {
            return SearchResponse.empty(System.nanoTime() - start);
        }

        // 2) candidate retrieval by n-gram overlap
        Set<String> queryGrams = new HashSet<>();
        for (String q : queryTerms) {
            queryGrams.addAll(NGrams.charNGramSet(q, index.n()));
        }
        int[] overlap = new int[index.numDocs()];
        for (String g : queryGrams) {
            for (int docId : index.docsWithNgram(g)) {
                overlap[docId]++;
            }
        }
        int needed = Math.max(1, (int) Math.ceil(config.minCandidateOverlap() * queryGrams.size()));

        // 3) score candidates
        List<ScoredDocument> scored = new ArrayList<>();
        int candidates = 0;
        double maxBm25 = 0;
        List<double[]> raw = new ArrayList<>();   // {docId, ngramScore, bm25}
        List<List<TermMatch>> rawMatches = new ArrayList<>();
        for (int docId = 0; docId < overlap.length; docId++) {
            if (overlap[docId] < needed) {
                continue;
            }
            candidates++;
            double ngramSum = 0;
            double bm25 = 0;
            List<TermMatch> used = new ArrayList<>();
            for (String q : queryTerms) {
                // expansions are sorted best-first, so the first one present in the doc is the best one
                for (TermMatch m : expansions.get(q)) {
                    if (index.termFrequency(m.matchedTerm(), docId) > 0) {
                        ngramSum += m.similarity();
                        bm25 += m.similarity() * bm25(m.matchedTerm(), docId);
                        used.add(m);
                        break;
                    }
                }
            }
            if (used.isEmpty()) {
                continue;
            }
            raw.add(new double[] {docId, ngramSum / queryTerms.size(), bm25});
            rawMatches.add(List.copyOf(used));
            maxBm25 = Math.max(maxBm25, bm25);
        }

        double w = config.ngramWeight();
        for (int i = 0; i < raw.size(); i++) {
            double[] r = raw.get(i);
            int docId = (int) r[0];
            double bm25Norm = maxBm25 > 0 ? r[2] / maxBm25 : 0;
            double score = w * r[1] + (1 - w) * bm25Norm;
            scored.add(new ScoredDocument(docId, index.document(docId).name(), score, r[1], r[2], bm25Norm,
                    rawMatches.get(i)));
        }
        scored.sort(RANKING);

        // 4) collapse and cut to top K
        List<ResultGroup> groups = config.collapseEnabled()
                ? ResultCollapser.collapse(scored, index, config.collapseThreshold(), config.collapseWindow(),
                        config.topK())
                : ResultCollapser.noCollapse(scored, config.topK());

        return new SearchResponse(groups, candidates, scored.size(), System.nanoTime() - start);
    }

    /** Vocabulary words similar to the query word, best first (ties broken alphabetically). */
    List<TermMatch> expand(String queryTerm) {
        if (!config.expandKnownTerms() && index.containsTerm(queryTerm)) {
            return List.of(new TermMatch(queryTerm, queryTerm, 1.0));
        }
        Set<String> grams = NGrams.charNGramSet(queryTerm, index.n());
        Map<String, Integer> shared = new HashMap<>();
        for (String g : grams) {
            for (String term : index.termsWithNgram(g)) {
                shared.merge(term, 1, Integer::sum);
            }
        }
        List<TermMatch> matches = new ArrayList<>();
        for (Map.Entry<String, Integer> e : shared.entrySet()) {
            int a = queryTerm.length();
            int b = e.getKey().length();
            if ((double) Math.min(a, b) / Math.max(a, b) < config.minLengthRatio()) {
                continue;
            }
            double sim = Similarity.dice(e.getValue(), grams.size(), index.termNgramCount(e.getKey()));
            if (sim >= config.minTermSimilarity()) {
                matches.add(new TermMatch(queryTerm, e.getKey(), sim));
            }
        }
        matches.sort(Comparator.comparingDouble(TermMatch::similarity).reversed()
                .thenComparing(TermMatch::matchedTerm));
        return matches.size() > config.maxExpansions() ? matches.subList(0, config.maxExpansions()) : matches;
    }

    /** Okapi BM25 contribution of one term to one document. */
    double bm25(String term, int docId) {
        int tf = index.termFrequency(term, docId);
        if (tf == 0) {
            return 0;
        }
        int n = index.numDocs();
        int df = index.documentFrequency(term);
        double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
        double k1 = config.bm25K1();
        double b = config.bm25B();
        double lengthNorm = index.avgDocLength() > 0 ? index.docLength(docId) / index.avgDocLength() : 1;
        return idf * tf * (k1 + 1) / (tf + k1 * (1 - b + b * lengthNorm));
    }
}
