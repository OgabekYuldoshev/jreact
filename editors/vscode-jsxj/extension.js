// VS Code client of the JSXJ language server (a Java process speaking LSP over stdio).
const fs = require('fs');
const vscode = require('vscode');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

function activate(context) {
  const cfg = vscode.workspace.getConfiguration('jsxj');
  const jar = cfg.get('server.jar') || context.asAbsolutePath('server/jsxj-lsp.jar');
  if (!fs.existsSync(jar)) {
    vscode.window.showWarningMessage('JSXJ: language server jar not found (' + jar + '). Highlighting works; run editors/vscode-jsxj/install.sh to build the server.');
    return;
  }
  client = new LanguageClient(
    'jsxj', 'JSXJ Language Server',
    { command: cfg.get('javaPath') || 'java', args: ['-jar', jar] },
    {
      documentSelector: [{ scheme: 'file', language: 'jsxj' }],
      initializationOptions: { classpath: cfg.get('classpath'), sourceRoots: cfg.get('sourceRoots') },
    });
  client.start().catch(err => vscode.window.showErrorMessage('JSXJ language server failed to start: ' + err.message));
  context.subscriptions.push({ dispose: () => client && client.stop() });
}

function deactivate() { return client ? client.stop() : undefined; }

module.exports = { activate, deactivate };
