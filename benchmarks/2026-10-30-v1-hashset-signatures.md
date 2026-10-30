# Benchmark v1: HashSet<String> shingle signatures (before optimization)

Measured 2026-10-30 with `java -Xmx2g -cp target/ngram-search.jar ngramsearch.bench.Benchmark` (default sizes).
Collapsing compared every result in the 100-result window against every group leader, using HashSet<String> shingles.

```
Java 17.0.6, Windows 11 amd64, 20 cores, max heap 2048 MB
seed 42, 300 timed queries per mode after 200 warm-up queries, n=3, 10% near-duplicates

| docs | index build (median of 3) | mode | mean | p50 | p95 | avg results shown | avg collapsed |
|---:|---:|---|---:|---:|---:|---:|---:|
| 1,000 | 158 ms | no collapse | 0.679 ms | 0.673 ms | 1.227 ms | 9.78 | 0.00 |
| 1,000 | | collapse (t=0.5) | 17.176 ms | 18.419 ms | 25.330 ms | 9.77 | 1.30 |
| 5,000 | 1434 ms | no collapse | 3.860 ms | 3.769 ms | 6.683 ms | 9.75 | 0.00 |
| 5,000 | | collapse (t=0.5) | 19.712 ms | 19.910 ms | 29.511 ms | 9.75 | 1.10 |
| 20,000 | 5850 ms | no collapse | 15.425 ms | 15.778 ms | 27.276 ms | 9.83 | 0.00 |
| 20,000 | | collapse (t=0.5) | 30.286 ms | 27.767 ms | 44.411 ms | 9.83 | 1.21 |
```
