#!/usr/bin/env bash
set -euo pipefail

# tools/generate-evidence.sh
# Executes live load benchmarks and collects raw evidence logs into reports/

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTS_DIR="${REPO_ROOT}/reports"
mkdir -p "${REPORTS_DIR}/benchmarks"

echo "=== Collecting Vibe System Evidence ==="
date -u +"Evidence generated at: %Y-%m-%dT%H:%M:%SZ" > "${REPORTS_DIR}/EVIDENCE.txt"
echo "Git Commit: $(git rev-parse HEAD)" >> "${REPORTS_DIR}/EVIDENCE.txt"
echo "Java Version: $(java -version 2>&1 | head -n 1)" >> "${REPORTS_DIR}/EVIDENCE.txt"

echo "--- 1. Running Substantive LOC Census ---"
"${REPO_ROOT}/tools/loc-census.sh" > "${REPORTS_DIR}/loc-census.txt"
cat "${REPORTS_DIR}/loc-census.txt"

echo "--- 2. Executing VibeLoadClient Uncoordinated Arrival Run ---"
./gradlew :benchmarks:runLoadClient --args="10 100 2000" 2>&1 | tee "${REPORTS_DIR}/benchmarks/load-client-output.txt" || true

echo "--- 3. Running JMH Microbenchmark Smoke Suite ---"
./gradlew :benchmarks:test --tests "com.vibe.benchmarks.BenchmarkSmokeTest" 2>&1 | tee "${REPORTS_DIR}/benchmarks/jmh-smoke.txt"

echo "Evidence generated in ${REPORTS_DIR}/"
