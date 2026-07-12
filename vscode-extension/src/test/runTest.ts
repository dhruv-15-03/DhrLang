import * as path from 'path';
import { runTests } from '@vscode/test-electron';

/**
 * Entry point for `npm test`. Launches a real, headless VS Code instance
 * (via @vscode/test-electron), installs this extension into it, opens the
 * fixture workspace, and runs the Mocha suite in ./suite/index.ts.
 *
 * This is the one thing static analysis (tsc / vsce package) cannot prove:
 * that the LanguageClient this extension wires up actually reaches the
 * "Running" state against a real `java -jar DhrLang.jar --lsp` process, and
 * that a real editor request (completion) is answered by that server rather
 * than by the static fallback provider.
 */
async function main() {
    try {
        const extensionDevelopmentPath = path.resolve(__dirname, '../../');
        const extensionTestsPath = path.resolve(__dirname, './suite/index');
        const workspacePath = path.resolve(__dirname, '../../src/test/fixtures/workspace');

        await runTests({
            extensionDevelopmentPath,
            extensionTestsPath,
            launchArgs: [workspacePath, '--disable-extensions']
        });
    } catch (err) {
        console.error('Failed to run DhrLang extension integration tests:', err);
        process.exit(1);
    }
}

main();
