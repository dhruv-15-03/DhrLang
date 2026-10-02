import * as assert from 'assert';
import * as path from 'path';
import * as vscode from 'vscode';
import { State } from 'vscode-languageclient/node';

const EXTENSION_ID = 'EnggWithDhruv.dhrlang-vscode';

async function waitFor<T>(fn: () => T | undefined, deadlineMs: number, pollMs = 250): Promise<T | undefined> {
    const deadline = Date.now() + deadlineMs;
    let value = fn();
    while (value === undefined && Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, pollMs));
        value = fn();
    }
    return value;
}

suite('DhrLang LSP client integration (end-to-end, headless VS Code)', () => {
    test('extension activates and the real Language Server reaches Running state', async function () {
        this.timeout(60000);

        const ext = vscode.extensions.getExtension(EXTENSION_ID);
        assert.ok(ext, `Extension "${EXTENSION_ID}" not found in the test host`);

        const api = await ext!.activate();
        assert.ok(api, 'activate() did not return an API object (getLanguageClient export missing)');
        assert.strictEqual(typeof api.getLanguageClient, 'function', 'activate() API is missing getLanguageClient()');

        const client = await waitFor(() => api.getLanguageClient(), 30000);
        assert.ok(
            client,
            'LanguageClient was never created. This usually means DhrLang.jar / java could not be ' +
                'resolved in the test workspace (the CI job must copy a built DhrLang.jar into ' +
                'src/test/fixtures/workspace/ before running `npm test`, or set dhrlang.jarPath).'
        );

        // Poll until the client actually finishes its handshake with the spawned
        // `java -jar DhrLang.jar --lsp` process instead of asserting immediately.
        const deadline = Date.now() + 30000;
        while (client!.state !== State.Running && Date.now() < deadline) {
            await new Promise((r) => setTimeout(r, 250));
        }
        assert.strictEqual(
            client!.state,
            State.Running,
            `Expected the DhrLang LanguageClient to reach State.Running, got state=${client!.state}`
        );
    });

    test('completion for a real local variable is answered by the Language Server (not the static fallback)', async function () {
        this.timeout(60000);

        const ext = vscode.extensions.getExtension(EXTENSION_ID)!;
        const api = await ext.activate();
        const client = await waitFor(() => api.getLanguageClient(), 30000);
        assert.ok(client, 'LanguageClient not available for completion test');
        const deadline0 = Date.now() + 30000;
        while (client!.state !== State.Running && Date.now() < deadline0) {
            await new Promise((r) => setTimeout(r, 250));
        }
        assert.strictEqual(client!.state, State.Running, 'Language Server never reached Running state');

        const fixtureUri = vscode.Uri.file(path.resolve(__dirname, '../../../src/test/fixtures/workspace/Scope.dhr'));
        const doc = await vscode.workspace.openTextDocument(fixtureUri);
        await vscode.window.showTextDocument(doc);

        // "myLocalCounter123" only exists as a local variable declared in this file.
        // The static fallback completion provider (a flat keyword/snippet list) has
        // no way to know about it - only the real, scope-aware Language Server does
        // (see DhrLangLspServer#collectLocalVarDecls). Finding it in the results
        // proves the request round-tripped to the real server.
        const marker = 'myLocalCounter123';
        const text = doc.getText();
        const declLine = text.split('\n').findIndex((l) => l.includes(marker));
        assert.ok(declLine >= 0, 'fixture marker variable not found in Scope.dhr');
        // Cursor on the blank line right after the declaration (mirrors the
        // equivalent server-side unit test in DhrLangLspServerTest).
        const position = new vscode.Position(declLine + 1, 0);

        const labelsIncludeMarker = () => {
            return vscode.commands
                .executeCommand<vscode.CompletionList>('vscode.executeCompletionItemProvider', fixtureUri, position)
                .then((list) => {
                    if (!list) return undefined;
                    const labels = list.items.map((i) => (typeof i.label === 'string' ? i.label : i.label.label));
                    return labels.includes(marker) ? labels : undefined;
                });
        };

        let labels: string[] | undefined;
        const deadline = Date.now() + 20000;
        while (!labels && Date.now() < deadline) {
            labels = await labelsIncludeMarker();
            if (!labels) {
                await new Promise((r) => setTimeout(r, 500));
            }
        }

        assert.ok(
            labels && labels.includes(marker),
            `Expected scope-aware completion from the real Language Server to include "${marker}"`
        );
    });
});
