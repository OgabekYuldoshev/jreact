#!/usr/bin/env bash
# E2E_BUILD=gradle runs it with a Gradle project (needs Gradle; installs the plugin to ~/.m2).
# End to end: jreact new -> jreact build (Tailwind, TeaVM, tests, Spring Boot jar) -> run the jar -> click through the app in jsdom.
#   ./build.sh spring-e2e        (needs Maven, node + npm, internet for the first Tailwind download)
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
PORT="${E2E_PORT:-8099}"
WORK="$(mktemp -d)"
trap 'kill "${SERVER:-0}" 2>/dev/null || true; rm -rf "$WORK"' EXIT

echo "== install JReact (plugin, web, starter, cli)"
mvn -B -q install -DskipTests -pl jreact-cli,jreact-spring-boot-starter,jreact-web,jreact-maven-plugin -am
BUILD="${E2E_BUILD:-maven}"
if [ "$BUILD" = gradle ]; then gradle --no-daemon -q -p jreact-gradle-plugin publishToMavenLocal; fi
(cd examples && npm install --silent --no-audit --no-fund)

echo "== jreact new"
"$ROOT/bin/jreact" new e2eapp --group com.acme --build "$BUILD" --dir "$WORK/e2eapp"
cd "$WORK/e2eapp"

echo "== jreact build"
"$ROOT/bin/jreact" build > "$WORK/build.log" 2>&1 || { tail -40 "$WORK/build.log"; exit 1; }
grep -E "Tests run:.*Fail" "$WORK/build.log" | tail -1 || true
if [ "$BUILD" = gradle ]; then JAR="$(ls server/build/libs/*.jar | grep -v plain | head -1)"; else JAR="$(ls server/target/*.jar | grep -v original | head -1)"; fi
echo "built $JAR"

echo "== run the jar on port $PORT"
java -jar "$JAR" --server.port="$PORT" > "$WORK/server.log" 2>&1 &
SERVER=$!
for i in $(seq 1 60); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '[]' "http://localhost:$PORT/_rpc/TodoApi/list" && break; sleep 1; done

echo "== HTTP checks"
fail() { echo "FAIL $1"; exit 1; }
[ "$(curl -s -o /dev/null -w '%{http_code}' -H 'Accept: text/html' "http://localhost:$PORT/api/nope")" = 404 ] || fail "unknown /api URL must be 404"
JS="$(curl -s "http://localhost:$PORT/" | grep -o 'assets/app\.[a-f0-9]*\.js' | head -1)"
[ -n "$JS" ] || fail "index.html has no hashed app.js"
curl -s -D - -o /dev/null -H 'Accept-Encoding: gzip' "http://localhost:$PORT/$JS" | grep -qi 'cache-control: .*immutable' || fail "hashed asset is not immutable"
curl -s -D - -o /dev/null -H 'Accept-Encoding: gzip' "http://localhost:$PORT/$JS" | grep -qi 'content-encoding: gzip' || fail "no gzip"
[ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/__jreact/version")" = 404 ] || fail "dev endpoints must not exist in production"
PAGE="$(curl -s -H 'Accept: text/html' "http://localhost:$PORT/")"
echo "$PAGE" | grep -q 'Read the README' || fail "the first page is not server rendered (no todo text in the HTML)"
echo "$PAGE" | grep -q '<script id="__jreact_data"' || fail "the page does not carry the loader answers"
[ "$(curl -s -o /dev/null -w '%{http_code}' -H 'Accept: text/html' "http://localhost:$PORT/some/client/route")" = 404 ] || fail "an unknown route renders the errorElement with status 404"
rpc() { curl -s -X POST -H 'Content-Type: application/json' -d "$2" -w ' [%{http_code}]' "http://localhost:$PORT/_rpc/TodoApi/$1"; }
rpc add '["from curl"]' | grep -q '"title":"from curl".*\[200\]' || fail "rpc add"
rpc add '[5]' | grep -q 'title: expected a string, got a number.*\[400\]' || fail "rpc type error must be a 400 that names the parameter"
rpc add '[" "]' | grep -q '\[422\]' || fail "rpc RpcException must keep its status"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: text/plain' -d '[]' "http://localhost:$PORT/_rpc/TodoApi/list")" = 415 ] || fail "rpc must refuse non-JSON bodies"
echo "ok   HTTP checks"

echo "== the app in jsdom"
node "$ROOT/tools/spring-e2e.js" "http://localhost:$PORT"

echo "== the same jar under a context path (/app)"
kill "$SERVER" 2>/dev/null || true; wait "$SERVER" 2>/dev/null || true
CPORT=$((PORT + 1))
java -jar "$JAR" --server.port="$CPORT" --server.servlet.context-path=/app > "$WORK/server-cp.log" 2>&1 &
SERVER=$!
for i in $(seq 1 60); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '[]' "http://localhost:$CPORT/app/_rpc/TodoApi/list" && break; sleep 1; done
CPAGE="$(curl -s -H 'Accept: text/html' "http://localhost:$CPORT/app/")"
echo "$CPAGE" | grep -q '<meta name="jreact-base" content="/app">' || fail "no jreact-base meta under a context path"
echo "$CPAGE" | grep -q 'src="/app/assets/app\.' || fail "assets are not under the context path"
echo "$CPAGE" | grep -q 'Read the README' || fail "SSR does not work under a context path"
node "$ROOT/tools/spring-e2e.js" "http://localhost:$CPORT/app"

if [ -n "${E2E_BROWSER:-}" ]; then
  # the browser run needs the server of the first part
  kill "$SERVER" 2>/dev/null || true; wait "$SERVER" 2>/dev/null || true
  java -jar "$JAR" --server.port="$PORT" > "$WORK/server.log" 2>&1 &
  SERVER=$!
  for i in $(seq 1 60); do curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -d '[]' "http://localhost:$PORT/_rpc/TodoApi/list" && break; sleep 1; done
fi

if [ -n "${E2E_BROWSER:-}" ]; then
  echo "== the app in Chromium (Playwright)"
  node "$ROOT/tools/browser-e2e.js" "http://localhost:$PORT"
fi
