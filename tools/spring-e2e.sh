#!/usr/bin/env bash
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
(cd examples && npm install --silent --no-audit --no-fund)

echo "== jreact new"
"$ROOT/bin/jreact" new e2eapp --group com.acme --dir "$WORK/e2eapp"
cd "$WORK/e2eapp"

echo "== jreact build"
"$ROOT/bin/jreact" build > "$WORK/build.log" 2>&1 || { tail -40 "$WORK/build.log"; exit 1; }
grep -E "Tests run:.*Fail" "$WORK/build.log" | tail -1
JAR="$(ls server/target/*.jar | grep -v original | head -1)"
echo "built $JAR"

echo "== run the jar on port $PORT"
java -jar "$JAR" --server.port="$PORT" > "$WORK/server.log" 2>&1 &
SERVER=$!
for i in $(seq 1 60); do curl -s -o /dev/null "http://localhost:$PORT/api/todos" && break; sleep 1; done

echo "== HTTP checks"
fail() { echo "FAIL $1"; exit 1; }
[ "$(curl -s -o /dev/null -w '%{http_code}' -H 'Accept: text/html' "http://localhost:$PORT/some/client/route")" = 200 ] || fail "client route does not get index.html"
[ "$(curl -s -o /dev/null -w '%{http_code}' -H 'Accept: text/html' "http://localhost:$PORT/api/nope")" = 404 ] || fail "unknown /api URL must be 404"
JS="$(curl -s "http://localhost:$PORT/" | grep -o 'assets/app\.[a-f0-9]*\.js' | head -1)"
[ -n "$JS" ] || fail "index.html has no hashed app.js"
curl -s -D - -o /dev/null -H 'Accept-Encoding: gzip' "http://localhost:$PORT/$JS" | grep -qi 'cache-control: .*immutable' || fail "hashed asset is not immutable"
curl -s -D - -o /dev/null -H 'Accept-Encoding: gzip' "http://localhost:$PORT/$JS" | grep -qi 'content-encoding: gzip' || fail "no gzip"
[ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/__jreact/version")" = 404 ] || fail "dev endpoints must not exist in production"
echo "ok   HTTP checks"

echo "== the app in jsdom"
node "$ROOT/tools/spring-e2e.js" "http://localhost:$PORT"
