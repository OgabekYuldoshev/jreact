#!/usr/bin/env bash
# Dev install: builds the language server jar, installs the client's npm dependency, and links this folder
# as a VS Code extension. Reload the VS Code window afterwards.
set -euo pipefail
src="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$src/../.." && pwd)"

"$repo/build.sh" lsp
mkdir -p "$src/server"
cp "$repo/build/jsxj-lsp.jar" "$src/server/jsxj-lsp.jar"
(cd "$src" && npm install --omit=dev --silent --no-audit --no-fund)

dst="${HOME}/.vscode/extensions/jreact.jsxj-0.2.0"
rm -rf "${HOME}"/.vscode/extensions/jreact.jsxj-* "$dst"
ln -s "$src" "$dst"
echo "linked $dst -> $src. Reload VS Code (Cmd+Shift+P > Developer: Reload Window)."
