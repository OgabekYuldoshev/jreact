# JRX in IntelliJ IDEA

No native plugin yet. The same language server as VS Code works through **LSP4IJ** (free, in the JetBrains Marketplace),
and the TextMate grammar gives highlighting.

1. Build the server: `./build.sh lsp` (creates `build/jrx-lsp.jar`).
2. Install the **LSP4IJ** plugin (Settings > Plugins > Marketplace).
3. Settings > Languages & Frameworks > Language Servers > **+** (New Language Server):
   - Name: `JRX`
   - Command: `java -jar /path/to/jreact/build/jrx-lsp.jar`
   - Mappings > File name patterns: `*.jrx`, language id `jrx`
4. Highlighting: Settings > Editor > TextMate Bundles > **+**, choose `editors/vscode-jrx` (it holds `syntaxes/jrx.tmLanguage.json`).
   Then Settings > Editor > File Types > TextMate: add the pattern `*.jrx`.

You get diagnostics (JSX and javac), completion (tags, props, Java expressions), hover and go to definition.
