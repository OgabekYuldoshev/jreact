# JSXJ for VS Code

Syntax highlighting for `.jsxj` (Java + JSX). Java is highlighted by VS Code's own Java grammar; JSX tags,
attributes and `{...}` expressions are layered on top (expressions are highlighted as Java).

Not included: completion, go-to-definition, diagnostics (needs a language server, see the roadmap).

## Install

```
./editors/vscode-jsxj/install.sh      # symlinks into ~/.vscode/extensions, then reload VS Code
```

Or package it: `npx @vscode/vsce package` inside this folder, then `code --install-extension jsxj-0.1.0.vsix`.

## Scopes

| What | Scope |
|---|---|
| `<div>` | `entity.name.tag.jsxj` |
| `<TodoItem>` (uppercase) | `support.class.component.jsxj` |
| `className=` | `entity.other.attribute-name.jsxj` |
| `{ ... }` | `meta.embedded.expression.jsxj` (Java inside) |
