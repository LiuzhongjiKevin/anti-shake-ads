#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d build/core-tests app/src/main/java/cn/returnguard/core/*.java tests/cn/returnguard/core/*.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -ea -cp build/core-tests cn.returnguard.core.GuardEngineTest
java -ea -cp build/core-tests cn.returnguard.core.ProtectionStateTest
java -ea -cp build/core-tests cn.returnguard.core.WindowReadinessTest
java -ea -cp build/core-tests cn.returnguard.core.CtripProbeTest
