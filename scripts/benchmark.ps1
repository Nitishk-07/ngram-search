# Builds the jar if needed, then runs the benchmark. Extra arguments are passed through,
# e.g. .\scripts\benchmark.ps1 --sizes 1000,5000 --queries 500
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Test-Path target/ngram-search.jar)) {
    .\mvnw.cmd -q -B package -DskipTests
}
java -Xmx2g -cp target/ngram-search.jar ngramsearch.bench.Benchmark @args
