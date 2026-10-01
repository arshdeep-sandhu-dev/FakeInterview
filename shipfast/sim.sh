#!/usr/bin/env bash
# Compiles the app and runs the Black Friday load simulation. Options:
#   ./sim.sh [--seed N] [--shoppers N] [--threads N]
set -euo pipefail
cd "$(dirname "$0")"

rm -rf build/classes
mkdir -p build/classes
find src/main/java -name '*.java' > build/main-sources.txt
javac -encoding UTF-8 -d build/classes @build/main-sources.txt
java -cp build/classes com.shipfast.sim.FlashSaleSimulation "$@"
