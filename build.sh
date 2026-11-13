#!/usr/bin/env bash
# JVM build without Maven: compiler -> runtime -> .jrx -> .java -> .class -> run.
set -euo pipefail
cd "$(dirname "$0")"

B=build
JAVAC="javac -encoding UTF-8 -Xlint:-options"

# Browser build (needs Maven, downloads TeaVM): .jrx -> .java -> .class -> app.js
#   ./build.sh web        -> examples/todo-web/target/web/{index.html,app.js}
#   ./build.sh web-test   -> same, then loads it in jsdom and clicks through the app (needs node + npm)
#   ./build.sh dev [port] [app...] -> dev server for todo, kanban, shop, dashboard, landing + gallery,
#                             rebuild on change + live reload (http://localhost:5173)
if [[ "${1:-}" == "dev" ]]; then
  shift
  exec node tools/dev-server.js "$@"
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

# Tailwind example apps (examples/kanban, shop, dashboard, landing): Maven + TeaVM, then Tailwind CLI, then a gallery.
#   ./build.sh examples        -> examples/dist/{index.html,kanban,shop,dashboard,landing,blog,contacts}   (needs Maven + node)
#   ./build.sh examples-test   -> same, then clicks through every app in jsdom
#   python3 -m http.server -d examples/dist 8080     # open http://localhost:8080
if [[ "${1:-}" == "examples" || "${1:-}" == "examples-test" ]]; then
  APPS="kanban shop dashboard landing blog contacts"
  echo "== example apps (Maven + TeaVM)"
  mvn -B -q -pl examples/kanban,examples/shop,examples/dashboard,examples/landing,examples/blog,examples/contacts -am package
  echo "== Tailwind CSS"
  (cd examples && npm install --silent --no-audit --no-fund)
  rm -rf examples/dist
  mkdir -p examples/dist
  for a in $APPS; do
    (cd examples && npx @tailwindcss/cli -i $a/src/main/styles/app.css -o $a/target/web/app.css --minify 2>&1 | tail -1)
    cp -R examples/$a/target/web examples/dist/$a
  done
  cp examples/gallery/index.html examples/dist/index.html
  (cd examples && npx @tailwindcss/cli -i gallery/gallery.css -o dist/gallery.css --minify 2>&1 | tail -1)
  echo "built examples/dist"
  if [[ "$1" == "examples-test" ]]; then
    echo "== example apps smoke test (jsdom)"
    (cd examples && node smoke.js dist)
  fi
  exit 0
fi

# Gradle plugin (dev.jreact.jrx): installs the compiler and core to ~/.m2, then builds and tests the plugin with TestKit
#   ./build.sh gradle-plugin   (needs Maven and Gradle)
if [[ "${1:-}" == "gradle-plugin" ]]; then
  mvn -B -q install -DskipTests -pl jreact-compiler,jreact-core -am
  exec gradle --no-daemon -p jreact-gradle-plugin build publishToMavenLocal
fi

# Spring Boot + JReact end to end: jreact new, jreact build, run the jar, click through the app in jsdom
#   ./build.sh spring-e2e      (needs Maven, node + npm; the first run downloads Tailwind)
if [[ "${1:-}" == "spring-e2e" ]]; then
  exec tools/spring-e2e.sh
fi

# Benchmark (js-framework-benchmark scenarios in headless Brave/Chrome): JReact vs React 18 vs vanilla DOM
#   ./build.sh bench [rounds]   -> table on stdout and examples/bench/target/results.md (needs Maven, node, Brave or Chrome)
if [[ "${1:-}" == "bench" ]]; then
  mvn -B -q -pl examples/bench -am package
  (cd examples && npm install --silent --no-audit --no-fund && cd bench && node run.js "${2:-3}")
  exit 0
fi

# Language server for .jrx (diagnostics, completion, hover, definition): build/jrx-lsp.jar
#   ./build.sh lsp        -> build/jrx-lsp.jar (used by editors/vscode-jrx/install.sh)
if [[ "${1:-}" == "lsp" ]]; then
  rm -rf "$B/compiler" "$B/lsp" "$B/jrx-lsp.jar"
  $JAVAC -d $B/compiler $(find jreact-compiler/src/main/java -name '*.java')
  $JAVAC -cp $B/compiler -d $B/lsp $(find jreact-lsp/src/main/java -name '*.java')
  jar --create --file $B/jrx-lsp.jar --main-class jreact.lsp.Main -C $B/compiler . -C $B/lsp .
  echo "built $B/jrx-lsp.jar"
  exit 0
fi

rm -rf "$B"

echo "== jrxc compiler"
$JAVAC -d $B/compiler $(find jreact-compiler/src/main/java -name '*.java')

echo "== jreact-core runtime"
$JAVAC -d $B/core $(find jreact-core/src/main/java -name '*.java')

echo "== jreact-scheduler"
$JAVAC -d $B/scheduler $(find jreact-scheduler/src/main/java -name '*.java')

echo "== jreact-reconciler"
$JAVAC -cp $B/core:$B/scheduler -d $B/reconciler $(find jreact-reconciler/src/main/java -name '*.java')

echo "== jreact-router"
$JAVAC -cp $B/core -d $B/router $(find jreact-router/src/main/java -name '*.java')

echo "== jreact-dom"
$JAVAC -cp $B/core:$B/scheduler:$B/reconciler -d $B/dom $(find jreact-dom/src/main/java -name '*.java')

echo "== jrx language server"
$JAVAC -cp $B/compiler -d $B/lsp $(find jreact-lsp/src/main/java -name '*.java')

if [[ "${1:-}" == "test" ]]; then
  echo "== compiler tests"
  $JAVAC -cp $B/compiler -d $B/compiler-test $(find jreact-compiler/src/test/java -name '*.java')
  java -cp $B/compiler:$B/compiler-test jreact.compiler.TranspilerTest
  java -cp $B/compiler:$B/compiler-test jreact.compiler.BundlerTest
  echo "== scheduler tests"
  $JAVAC -cp $B/scheduler -d $B/scheduler-test $(find jreact-scheduler/src/test/java -name '*.java')
  java -cp $B/scheduler:$B/scheduler-test jreact.scheduler.SchedulerTest
  echo "== reconciler tests"
  $JAVAC -cp $B/core:$B/scheduler:$B/reconciler -d $B/reconciler-test $(find jreact-reconciler/src/test/java -name '*.java')
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/reconciler-test jreact.reconciler.ReconcilerTest
  echo "== dom tests"
  $JAVAC -cp $B/core:$B/scheduler:$B/reconciler:$B/dom -d $B/dom-test $(find jreact-dom/src/test/java -name '*.java')
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/dom:$B/dom-test jreact.dom.DomTest
  echo "== router tests"
  $JAVAC -cp $B/core:$B/scheduler:$B/reconciler:$B/dom:$B/router -d $B/router-test $(find jreact-router/src/test/java -name '*.java')
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/dom:$B/router:$B/router-test jreact.router.RouterTest
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/dom:$B/router:$B/router-test jreact.router.DataRouterTest
  java -cp $B/core:$B/scheduler:$B/reconciler:$B/dom:$B/router:$B/router-test jreact.router.JsonTest
  echo "== language server tests"
  $JAVAC -cp $B/compiler:$B/lsp -d $B/lsp-test $(find jreact-lsp/src/test/java -name '*.java')
  java -cp $B/compiler:$B/lsp:$B/lsp-test jreact.lsp.LspTest
  exit 0
fi

echo "== .jrx -> .java"
java -cp $B/compiler jreact.compiler.JrxCompiler $B/gen jreact-core/src/main/java examples/todo/src/main/jrx examples/todo/src/main/java

echo "== javac"
$JAVAC -cp $B/core -d $B/app $(find $B/gen examples/todo/src/main/java -name '*.java')

echo "== run app.Main"
java -Dstdout.encoding=UTF-8 -cp $B/core:$B/app app.Main
