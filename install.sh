#!/usr/bin/env sh
# Installs the jreact command. Download this file, read it, run it:
#   curl -fsSLO https://raw.githubusercontent.com/OgabekYuldoshev/jreact/main/install.sh && sh install.sh
# Needs JDK 17+. Installs into ~/.jreact (JREACT_HOME). JREACT_VERSION=v0.1.0 picks a release, default is the latest.
set -eu

HOME_DIR="${JREACT_HOME:-$HOME/.jreact}"
VERSION="${JREACT_VERSION:-latest}"
if [ -n "${JREACT_CLI_URL:-}" ]; then
  URL="$JREACT_CLI_URL"
elif [ "$VERSION" = latest ]; then
  URL="https://github.com/OgabekYuldoshev/jreact/releases/latest/download/jreact-cli.jar"
else
  URL="https://github.com/OgabekYuldoshev/jreact/releases/download/$VERSION/jreact-cli.jar"
fi

command -v java >/dev/null 2>&1 || { echo "jreact: java not found. Install a JDK 17+ first (https://adoptium.net)." >&2; exit 1; }

mkdir -p "$HOME_DIR/bin"
echo "Downloading $URL"
case "$URL" in
  file://*) cp "${URL#file://}" "$HOME_DIR/jreact-cli.jar" ;;
  *) curl -fsSL "$URL" -o "$HOME_DIR/jreact-cli.jar.tmp" && mv "$HOME_DIR/jreact-cli.jar.tmp" "$HOME_DIR/jreact-cli.jar" ;;
esac

cat > "$HOME_DIR/bin/jreact" <<LAUNCHER
#!/usr/bin/env sh
exec java -jar "$HOME_DIR/jreact-cli.jar" "\$@"
LAUNCHER
chmod +x "$HOME_DIR/bin/jreact"

echo "Installed: $HOME_DIR/bin/jreact"
case ":$PATH:" in
  *":$HOME_DIR/bin:"*) echo "Try it: jreact doctor" ;;
  *) echo
     echo "Add it to your PATH (then open a new terminal):"
     echo "  echo 'export PATH=\"$HOME_DIR/bin:\$PATH\"' >> ~/.zshrc     # bash: ~/.bashrc"
     echo "Then: jreact doctor" ;;
esac
