#!/usr/bin/env sh
# Builds the jar if needed, then runs the benchmark. Extra arguments are passed through,
# e.g. ./scripts/benchmark.sh --sizes 1000,5000 --queries 500
set -e
cd "$(dirname "$0")/.."
if [ ! -f target/ngram-search.jar ]; then
  ./mvnw -q -B package -DskipTests
fi
java -Xmx2g -cp target/ngram-search.jar ngramsearch.bench.Benchmark "$@"
