# JRX for VS Code

`.jrx` (Java + JSX) support: syntax highlighting and a language server.

- **Highlighting:** Java by VS Code's own Java grammar; JSX tags, attributes and `{...}` expressions layered on top.
- **Language server** (`jreact-lsp`, a Java process over stdio):
  - diagnostics: JSX errors on every keystroke, javac errors shortly after you stop typing
  - completion: component names and props after `<`, HTML tags and attributes
  - hover: `record Props(...)` on a tag, the type on a prop
  - go to definition: from a tag to its component class

Completion/hover inside `{...}` Java expressions is not provided (that is the Java extension's job).

## Install

```
./editors/vscode-jrx/install.sh      # builds the server jar, npm install, links into ~/.vscode/extensions
```

Then reload VS Code. Requires a JDK 17+ on the PATH (javac diagnostics need a JDK, not a JRE) and Node/npm for the
install step. To package instead: run `install.sh` once (it creates `server/jrx-lsp.jar`), then `npx @vscode/vsce package`.

## Settings

| Setting | Meaning |
|---|---|
| `jrx.javaPath` | java executable (default `java`) |
| `jrx.server.jar` | server jar (default: the bundled `server/jrx-lsp.jar`) |
| `jrx.sourceRoots` | extra `.java` source folders for javac and component lookup, e.g. `jreact-core/src/main/java` when it is outside the workspace |
| `jrx.classpath` | jars/class folders for javac, e.g. the jreact-core jar |

Folders inside the workspace are found automatically (their `package` lines give the source roots).

## Highlighting scopes

| What | Scope |
|---|---|
| `<div>` | `entity.name.tag.jrx` |
| `<TodoItem>` (uppercase) | `support.class.component.jrx` |
| `className=` | `entity.other.attribute-name.jrx` |
| `{ ... }` | `meta.embedded.expression.jrx` (Java inside) |
