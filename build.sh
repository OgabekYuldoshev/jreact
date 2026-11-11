#!/usr/bin/env bash
# JVM build without Maven: compiler -> runtime -> .jsxj -> .java -> .class -> run.
set -euo pipefail
cd "$(dirname "$0")"

B=build
JAVAC="javac -encoding UTF-8 -Xlint:-options"
rm -rf "$B"

echo "== jsxjc compiler"
$JAVAC -d $B/compiler $(find jreact-compiler/src/main/java -name '*.java')

echo "== jreact-core runtime"
$JAVAC -d $B/core $(find jreact-core/src/main/java -name '*.java')

echo "== jreact-scheduler"
$JAVAC -d $B/scheduler $(find jreact-scheduler/src/main/java -name '*.java')

echo "== jreact-reconciler"
$JAVAC -cp $B/core:$B/scheduler -d $B/reconciler $(find jreact-reconciler/src/main/java -name '*.java')

if [[ "${1:-}" == "test" ]]; then
  echo "== compiler tests"
  $JAVAC -cp $B/compiler -d $B/compiler-test $(find jreact-compiler/src/test/java -name '*.java')
  java -cp $B/compiler:$B/compiler-test jreact.compiler.TranspilerTest
  echo "== scheduler tests"
  $JAVAC -cp $B/scheduler -d $B/scheduler-test $(find jreact-scheduler/src/test/java -name '*.java')
  java -cp $B/scheduler:$B/scheduler-test jreact.scheduler.SchedulerTest
  echo "== reconciler tests"
  $JAVAC -cp $B/core:$B/scheduler:$B/reconciler -d $B/reconciler-test $(find jreact-reconciler/src/test/java -name '*.java')
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/reconciler-test jreact.reconciler.ReconcilerTest
  exit 0
fi

echo "== .jsxj -> .java"
java -cp $B/compiler jreact.compiler.JsxjCompiler $B/gen examples/todo/src/main/jsxj examples/todo/src/main/java

echo "== javac"
$JAVAC -cp $B/core -d $B/app $(find $B/gen examples/todo/src/main/java -name '*.java')

echo "== run app.Main"
java -Dstdout.encoding=UTF-8 -cp $B/core:$B/app app.Main
