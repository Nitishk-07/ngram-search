# Benchmark v2: sorted long[] signatures + top-K bounded clustering

Measured 2026-10-30 with `java -Xmx2g -cp target/ngram-search.jar ngramsearch.bench.Benchmark` (default sizes),
same machine, seed and queries as v1.

Changes from v1:
- shingle signatures stored as sorted 64-bit FNV-1a hashes; Jaccard by two-pointer merge instead of HashSet lookups
- once topK groups exist, a result is only compared against those leaders (output proven identical by
  `ResultCollapserTest.stoppingAtTopKGroupsGivesSameAnswerAsFullClustering`)

```
Java 17.0.6, Windows 11 amd64, 20 cores, max heap 2048 MB
seed 42, 300 timed queries per mode after 200 warm-up queries, n=3, 10% near-duplicates

| docs | index build (median of 3) | mode | mean | p50 | p95 | avg results shown | avg collapsed |
|---:|---:|---|---:|---:|---:|---:|---:|
| 1,000 | 160 ms | no collapse | 0.415 ms | 0.422 ms | 0.718 ms | 9.78 | 0.00 |
| 1,000 | | collapse (t=0.5) | 1.397 ms | 1.512 ms | 1.935 ms | 9.77 | 1.30 |
| 5,000 | 845 ms | no collapse | 3.989 ms | 3.924 ms | 6.869 ms | 9.75 | 0.00 |
| 5,000 | | collapse (t=0.5) | 5.356 ms | 5.432 ms | 8.457 ms | 9.75 | 1.10 |
| 20,000 | 3277 ms | no collapse | 16.706 ms | 18.482 ms | 26.798 ms | 9.83 | 0.00 |
| 20,000 | | collapse (t=0.5) | 18.028 ms | 19.733 ms | 27.861 ms | 9.83 | 1.21 |
```

Collapse overhead (mean, collapse minus no collapse):

| docs | v1 | v2 |
|---:|---:|---:|
| 1,000 | 16.5 ms | 1.0 ms |
| 5,000 | 15.9 ms | 1.4 ms |
| 20,000 | 14.9 ms | 1.3 ms |

Average collapsed counts are identical to v1, so the optimization did not change results.
Note: single runs on a laptop; expect a few ms of run-to-run noise, especially at 20,000 docs.
