#!/usr/bin/env bash
# JVM build without Maven: compiler -> runtime -> .jsxj -> .java -> .class -> run.
set -euo pipefail
cd "$(dirname "$0")"

B=build
JAVAC="javac -encoding UTF-8 -Xlint:-options"

# Browser build (needs Maven, downloads TeaVM): .jsxj -> .java -> .class -> app.js
#   ./build.sh web        -> examples/todo-web/target/web/{index.html,app.js}
#   ./build.sh web-test   -> same, then loads it in jsdom and clicks through the app (needs node + npm)
#   ./build.sh dev [port] -> dev server with rebuild on change + live reload (http://localhost:5173)
if [[ "${1:-}" == "dev" ]]; then
  exec node examples/todo-web/dev/dev-server.js "${2:-5173}"
fi

if [[ "${1:-}" == "web" || "${1:-}" == "web-test" ]]; then
  echo "== browser build (Maven + TeaVM)"
  mvn -B -q -pl examples/todo-web -am package
  echo "built examples/todo-web/target/web/app.js"
  if [[ "$1" == "web-test" ]]; then
    echo "== browser smoke test (jsdom)"
    (cd examples/todo-web/smoke && npm install --silent --no-audit --no-fund && node smoke.js ../target/web)
  fi
  exit 0
fi

rm -rf "$B"

echo "== jsxjc compiler"
$JAVAC -d $B/compiler $(find jreact-compiler/src/main/java -name '*.java')

echo "== jreact-core runtime"
$JAVAC -d $B/core $(find jreact-core/src/main/java -name '*.java')

echo "== jreact-scheduler"
$JAVAC -d $B/scheduler $(find jreact-scheduler/src/main/java -name '*.java')

echo "== jreact-reconciler"
$JAVAC -cp $B/core:$B/scheduler -d $B/reconciler $(find jreact-reconciler/src/main/java -name '*.java')

echo "== jreact-dom"
$JAVAC -cp $B/core:$B/scheduler:$B/reconciler -d $B/dom $(find jreact-dom/src/main/java -name '*.java')

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
  echo "== dom tests"
  $JAVAC -cp $B/core:$B/scheduler:$B/reconciler:$B/dom -d $B/dom-test $(find jreact-dom/src/test/java -name '*.java')
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/dom:$B/dom-test jreact.dom.DomTest
  exit 0
fi

echo "== .jsxj -> .java"
java -cp $B/compiler jreact.compiler.JsxjCompiler $B/gen examples/todo/src/main/jsxj examples/todo/src/main/java

echo "== javac"
$JAVAC -cp $B/core -d $B/app $(find $B/gen examples/todo/src/main/java -name '*.java')

echo "== run app.Main"
java -Dstdout.encoding=UTF-8 -cp $B/core:$B/app app.Main
