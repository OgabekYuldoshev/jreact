// VS Code client of the JRX language server (a Java process speaking LSP over stdio).
const fs = require('fs');
const vscode = require('vscode');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

function activate(context) {
  const cfg = vscode.workspace.getConfiguration('jrx');
  const jar = cfg.get('server.jar') || context.asAbsolutePath('server/jrx-lsp.jar');
  if (!fs.existsSync(jar)) {
    vscode.window.showWarningMessage('JRX: language server jar not found (' + jar + '). Highlighting works; run editors/vscode-jrx/install.sh to build the server.');
    return;
  }
  client = new LanguageClient(
    'jrx', 'JRX Language Server',
    { command: cfg.get('javaPath') || 'java', args: ['-jar', jar] },
    {
      documentSelector: [{ scheme: 'file', language: 'jrx' }],
      initializationOptions: { classpath: cfg.get('classpath'), sourceRoots: cfg.get('sourceRoots') },
    });
  client.start().catch(err => vscode.window.showErrorMessage('JRX language server failed to start: ' + err.message));
  context.subscriptions.push({ dispose: () => client && client.stop() });
}

function deactivate() { return client ? client.stop() : undefined; }

module.exports = { activate, deactivate };
