.PHONY: all bootstrap dev demo test benchmark acceptance verify loc clean help

# Default target
all: verify

help:
	@echo "Vibe Distributed Social Messenger - Build & Operations"
	@echo ""
	@echo "Available targets:"
	@echo "  bootstrap   - Verify toolchains (Java 21+, Node 20+), install deps, compile all modules"
	@echo "  dev         - Launch local single-node Vibe server (port 9000, inspector 9001)"
	@echo "  demo        - Launch 3-node Ratis cluster, import baseline data, and start desktop client"
	@echo "  test        - Run full test suite across all subprojects"
	@echo "  benchmark   - Execute JMH benchmarks and VibeLoadClient, generating evidence logs"
	@echo "  acceptance  - Execute acceptance test campaign (fuzzing, crash recovery, consensus)"
	@echo "  verify      - Run diff check, loc census, full test suite, and acceptance suite"
	@echo "  loc         - Execute substantive non-test source line census"
	@echo "  clean       - Clean build outputs and temporary run directories"

bootstrap:
	@echo "=== Checking toolchains ==="
	@java -version 2>&1 | head -n 1
	@node --version
	@echo "=== Building TypeScript SDK and Web Client ==="
	@npm --prefix sdk-typescript install
	@npm --prefix sdk-typescript run build
	@npm --prefix web install
	@npm --prefix web run build
	@echo "=== Compiling Java Multi-Module Project ==="
	@./gradlew assemble

dev:
	@echo "=== Starting Vibe Server (Standalone WAL Mode) ==="
	@./gradlew :server:run --args="--port 9000 --http-port 9001 --data-dir ./run/server-data"

demo:
	@echo "=== Starting Vibe 3-Node Ratis Consensus Demo ==="
	@./gradlew :desktop:run

test:
	@echo "=== Running Full Test Suite ==="
	@./gradlew test

benchmark:
	@echo "=== Running Benchmarks & Generating Raw Evidence ==="
	@chmod +x tools/generate-evidence.sh
	@./tools/generate-evidence.sh

acceptance:
	@echo "=== Executing Acceptance Test Campaign ==="
	@./gradlew :protocol:test --tests "com.vibe.protocol.ProtocolFuzzingAcceptanceTest"
	@./gradlew :server:test --tests "com.vibe.server.ProtocolFuzzingSocketAcceptanceTest"
	@./gradlew :server:test --tests "com.vibe.server.AuthorizationMatrixAcceptanceTest"
	@./gradlew :storage-local:test --tests "com.vibe.storage.local.CrashRecoveryAcceptanceTest"
	@./gradlew :storage-ratis:test --tests "com.vibe.storage.ratis.RatisReplicationInvariantsAcceptanceTest"
	@./gradlew :delivery:test --tests "com.vibe.delivery.BackpressureAndSlowConsumerAcceptanceTest"
	@./gradlew :delivery:test --tests "com.vibe.delivery.ConcurrentClientsAcceptanceTest"
	@./gradlew :domain:test --tests "com.vibe.domain.reference.DomainHistoryAcceptanceTest"

loc:
	@chmod +x tools/loc-census.sh
	@./tools/loc-census.sh

verify:
	@echo "=== Verifying Git Diff Integrity ==="
	@git diff --check
	@echo "=== Running Substantive Source Line Census ==="
	@$(MAKE) loc
	@echo "=== Running Full Test Suite ==="
	@$(MAKE) test
	@echo "=== Running Acceptance Campaign ==="
	@$(MAKE) acceptance
	@echo "=== Verification Succeeded ==="

clean:
	@./gradlew clean
	@rm -rf run/ reports/
