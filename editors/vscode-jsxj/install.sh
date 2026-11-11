#!/usr/bin/env bash
# Dev install: link this folder as a VS Code extension. Reload the window afterwards.
set -euo pipefail
src="$(cd "$(dirname "$0")" && pwd)"
dst="${HOME}/.vscode/extensions/jreact.jsxj-0.1.0"
rm -rf "$dst"; ln -s "$src" "$dst"
echo "linked $dst -> $src. Reload VS Code (Cmd+Shift+P > Developer: Reload Window)."
