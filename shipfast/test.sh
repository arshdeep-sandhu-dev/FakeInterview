#!/usr/bin/env bash
# Compiles the project and runs the JUnit 5 test suite. Extra arguments are passed to the JUnit
# console launcher, e.g.:
#   ./test.sh                                              # all tests
#   ./test.sh --select-class com.shipfast.order.OrderServiceTest
#   ./test.sh --select-method com.shipfast.common.MoneyTest#addsAmounts
set -euo pipefail
cd "$(dirname "$0")"

JUNIT_VERSION=1.10.2
JUNIT_JAR="lib/junit-platform-console-standalone-${JUNIT_VERSION}.jar"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac

if [ ! -f "$JUNIT_JAR" ]; then
  mkdir -p lib
  echo "Downloading JUnit console launcher..."
  curl -sSfL -o "$JUNIT_JAR" \
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/junit-platform-console-standalone-${JUNIT_VERSION}.jar"
fi

rm -rf build/classes build/test-classes
mkdir -p build/classes build/test-classes
find src/main/java -name '*.java' > build/main-sources.txt
find src/test/java -name '*.java' > build/test-sources.txt

javac -encoding UTF-8 -d build/classes @build/main-sources.txt
javac -encoding UTF-8 -cp "build/classes${SEP}${JUNIT_JAR}" -d build/test-classes @build/test-sources.txt

case " $* " in
  *" --select"*|*" --scan"*) ;;
  *) set -- --scan-class-path "$@" ;;
esac
java -jar "$JUNIT_JAR" execute \
  --class-path "build/classes${SEP}build/test-classes" \
  --disable-banner --details=tree "$@"
