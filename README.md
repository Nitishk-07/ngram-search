# N-Gram Search Engine

A typo-tolerant document search engine in plain Java 17. It finds documents even when the query is
misspelled, ranks them with a blend of n-gram similarity and BM25, and collapses near-duplicate
documents (reposts, lightly edited copies) into a single result.

The core algorithms are implemented from scratch: n-gram inverted index, Dice/Jaccard similarity,
BM25, and shingle-based duplicate clustering. No search library is used. The only dependency is
JUnit 5, for tests.

```
search> council budjet
2 matching documents, 8 candidates, 1 shown, 1 collapsed  (3.02 ms)

 1. city-budget.txt  0.875
    0.875 = 0.50 x ngram 0.750 + 0.50 x bm25 1.000 (raw 3.710) | matched: council, budjet->budget (0.50)
    The city council approved a new budget on Tuesday that adds funding for public transit, r...
    +1 near-duplicate collapsed: city-budget-update.txt [id 2, J=0.72]
```

## Features

- **Character n-gram index** (configurable n, default 3) with `$` boundary padding, so prefixes and suffixes count
- **Typo tolerance:** misspelled query words are matched to vocabulary words by n-gram Dice similarity
  (`dijsktra -> dijkstra`, `espreso -> espresso`, `budjet -> budget`)
- **Hybrid ranking:** `score = w * ngramScore + (1 - w) * bm25Normalized`, weight configurable
- **Explainable results:** every result prints its score components and which words matched what
- **Near-duplicate collapsing:** word-shingle Jaccard plus deterministic leader clustering.
  Configurable threshold, can be turned off, and collapsed document ids and counts are kept
- **Deterministic:** same corpus + same query = same output; ties break by document name
- **Interactive CLI:** change n, threshold, weight and top-k without restarting
- **Benchmark harness** on generated corpora, with recorded results in [`benchmarks/`](benchmarks/)

## Quick start

Requires Java 17+. Maven is not needed; the included wrapper downloads it.

```bash
./mvnw package                      # compile, run all tests, build target/ngram-search.jar
java -jar target/ngram-search.jar --dir samples
```

On Windows use `mvnw.cmd package`.

One-shot query (no prompt):

```bash
java -jar target/ngram-search.jar --dir samples --query "dijsktra shortest path"
```

### CLI options

| option | default | meaning |
|---|---|---|
| `--dir <path>` | `samples` | directory of `.txt` files (searched recursively) |
| `--n <int>` | 3 | character n-gram size |
| `--shingle <int>` | 3 | word shingle size for duplicate detection |
| `--threshold <0..1>` | 0.5 | collapse when shingle Jaccard >= threshold |
| `--no-collapse` | | turn collapsing off |
| `--weight <0..1>` | 0.5 | weight of the n-gram score (BM25 gets 1 - weight) |
| `--top <int>` | 10 | number of result groups |
| `--query <text>` | | run one query and exit |
| `--no-color` | | plain output |

Interactive commands: `:n 4`, `:threshold 0.7`, `:collapse off`, `:weight 0.3`, `:top 5`, `:stats`, `:help`, `:quit`.

## Architecture

```
                    .txt files
                        |
                 DocumentLoader            sorted paths -> stable doc ids
                        |
                 TextNormalizer            NFKD, strip accents, lowercase, drop 's, punctuation -> space
                        |
                  InvertedIndex.build
        +---------------+----------------+-------------------+
        |               |                |                   |
  ngram -> docs    ngram -> terms    term -> (doc -> tf)   doc -> shingle signature
  (candidates)     (fuzzy matching)  (BM25)                (collapsing)

  query --> SearchEngine.search
              1. normalize, drop stopwords
              2. expand each word to similar vocabulary words      (ngram -> terms)
              3. candidate docs = enough shared query n-grams     (ngram -> docs)
              4. score: n-gram Dice + BM25, normalize, weighted sum
              5. sort (score desc, name asc)
              6. ResultCollapser: leader clustering on shingle Jaccard
              7. top K groups
```

| package | classes |
|---|---|
| `ngramsearch.text` | `TextNormalizer`, `NGrams`, `Similarity` |
| `ngramsearch.index` | `Document`, `DocumentLoader`, `InvertedIndex`, `IntList` |
| `ngramsearch.search` | `SearchConfig`, `SearchEngine`, `ResultCollapser`, `ScoredDocument`, `ResultGroup`, `TermMatch`, `SearchResponse` |
| `ngramsearch.cli` | `Main` |
| `ngramsearch.bench` | `Benchmark`, `CorpusGenerator` |

## How it works

### 1. Normalization and n-grams

`"Dijkstra's Algorithm!"` becomes `dijkstra algorithm`. Each word is padded and cut into
overlapping character n-grams:

```
dijkstra (n=3) -> $di dij ijk jks kst str tra ra$
dijsktra (n=3) -> $di dij ijs jsk skt ktr tra ra$
                  shared: $di dij tra ra$   Dice = 2*4 / (8+8) = 0.50
```

A typo only damages the n-grams that touch it, so a misspelled word still shares most of its
n-grams with the correct one. A small English stopword list ("the", "and", ...) is not indexed.

### 2. Fuzzy term matching

For each query word:
- If the word **exists in the vocabulary**, it matches only itself. Otherwise "rover" would also match "over".
  You can turn this off with `expandKnownTerms`.
- If it doesn't, the engine counts shared n-grams with every vocabulary word through the
  `ngram -> terms` index. It keeps words with Dice >= `minTermSimilarity` (0.4), keeps only words of
  similar length (length ratio >= 0.6, which stops "budjet" from matching "jet"), and takes the best 5.

### 3. Candidate retrieval

Using the `ngram -> docs` postings, count how many of the query's n-grams each document contains.
Only documents containing at least `minCandidateOverlap` (20%) of them are scored.

### 4. Scoring

For each query word, take the best matching word present in the document:

- **n-gram score** = average over query words of that best match's Dice similarity (0 if none).
  It rewards documents that contain *all* query words, spelled close to the query.
- **BM25** (k1 = 1.2, b = 0.75) over the matched words, each word's contribution multiplied by its
  similarity, so a fuzzy match counts less than an exact one:

  ```
  idf(t)     = ln(1 + (N - df + 0.5) / (df + 0.5))
  bm25(t, d) = idf(t) * tf * (k1 + 1) / (tf + k1 * (1 - b + b * |d| / avgdl))
  ```
  It rewards rare words, repeated words and short documents.
- BM25 has no upper bound, so it is divided by the best BM25 in this result set to land in [0, 1].
- **final** = `w * ngram + (1 - w) * bm25Normalized` with `w = 0.5` by default.

### 5. Near-duplicate collapsing

Each document's signature is the set of its 3-word shingles ("the city council", "city council
approved", ...), stored as sorted 64-bit hashes. Similarity is Jaccard, computed with a two-pointer
merge. Editing one word changes only the 3 shingles that contain it, so a lightly edited repost
keeps a high Jaccard score while unrelated documents score near 0.

Clustering is **leader clustering**, done in rank order:
1. Compare the result to each existing group's leader, oldest first.
2. Join the first group with Jaccard >= threshold; otherwise start a new group.

Since results arrive best-first, every group's representative is its highest-ranked member. The
clustering is deterministic and deliberately **not transitive**: A~B and B~C does not drag C into
A's group. Once K groups exist, results are only compared to those K leaders. This gives identical
output, which a test verifies, at a fraction of the cost.

## Complexity

T = total tokens, L = average word length, N = documents, V = vocabulary size, Q = query n-grams,
P = average postings length, C = candidates, E = expansions per word, W = collapse window, K = top-k,
S = shingles per document.

| operation | time |
|---|---|
| build index | O(T * L) n-grams, O(T * k) shingles, O(N * S log S) to sort signatures |
| fuzzy expansion of one word | O(sum of `ngram -> terms` postings for its n-grams) |
| candidate retrieval | O(Q * P) + O(N) scan of the overlap counter |
| scoring | O(C * words * E), hash lookups |
| ranking | O(C log C) |
| collapsing | O(W * K * S) |

Memory is dominated by the `ngram -> docs` postings (one int per distinct n-gram per document),
stored as primitive `int[]` to avoid boxing.

## Benchmarks

Measured with `scripts/benchmark.sh` (or `scripts\benchmark.ps1`). Corpus: synthetic Zipf-distributed
made-up words, 60 to 240 words per document, 10% near-duplicates. Queries: 1 to 3 words from random
documents, half with one typo; 300 timed queries per mode after 200 warm-up queries.
Hardware: Windows 11 laptop, 20 cores, Java 17.0.6, `-Xmx2g`. Single run, so treat a few ms as noise.

| docs | index build | search, no collapse (mean / p95) | search, collapse (mean / p95) |
|---:|---:|---:|---:|
| 1,000 | 160 ms | 0.42 / 0.72 ms | 1.40 / 1.94 ms |
| 5,000 | 845 ms | 3.99 / 6.87 ms | 5.36 / 8.46 ms |
| 20,000 | 3,277 ms | 16.71 / 26.80 ms | 18.03 / 27.86 ms |

The first version stored signatures as `HashSet<String>` and compared every result against every
group. That made collapsing cost about 15 ms per query
([v1 results](benchmarks/2026-10-30-v1-hashset-signatures.md)). Switching to sorted hash arrays
and the top-K bound brought it down to about 1 to 1.4 ms with identical results
([v2 results](benchmarks/2026-10-30-v2-hashed-signatures.md)).

## Testing

```bash
./mvnw test
```

56 JUnit 5 tests cover:
- normalization: accents, apostrophes, possessives, empty input
- n-gram generation
- Jaccard and Dice values
- index postings and statistics, configurable n, empty corpus, the file loader
- exact matches and typo-tolerant matches (transposition, deletion, substitution, insertion)
- unrelated queries, plus empty and stopword-only queries
- the score formula and deterministic tie-breaking
- regression tests for the "rover/over" and "budjet/jet" false matches
- collapsing: near-duplicates, preserved ids, disabled mode, inclusive threshold boundary (J = 0.5
  exactly), non-transitivity, the window limit, and the top-K bound giving identical results

## Limitations

- **In memory only.** The index is rebuilt on every start, with no persistence.
- **Per-query O(N) scan** of the candidate counter. Fine at tens of thousands of documents, but it
  dominates latency at 20,000. A sparse counter or a top-n-gram heap would fix it.
- **No stemming.** "landing" fuzzy-matches "landed" and "lander", but also "expanding"
  (shared "-nding"). Such matches rank low but can appear.
- **Fuzzy matching only for unknown words.** A real word typed by mistake ("form" for "from") isn't corrected.
- **BM25 is normalized per query**, so scores are comparable within one result list, not across queries.
- **Leader clustering depends on rank order**, which is deterministic but means a different query can group
  the same documents differently.
- Signature hashes are 64-bit, so a collision could in theory inflate a Jaccard score.
- English-only stopwords; no phrase or boolean queries.

## Project layout

```
src/main/java/ngramsearch/   text/ index/ search/ cli/ bench/
src/test/java/ngramsearch/   unit tests
samples/                     15 example documents, including duplicate clusters (Mars rover x3, city budget x2)
benchmarks/                  recorded benchmark output
scripts/                     benchmark runners
INTERVIEW_GUIDE.md           concepts and trade-offs, explained
```
