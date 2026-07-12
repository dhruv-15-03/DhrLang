import * as vscode from 'vscode';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

const execAsync = promisify(exec);

let extensionContext: vscode.ExtensionContext;
let diagnosticCollection: vscode.DiagnosticCollection;
let outputChannel: vscode.OutputChannel;
let languageClient: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext) {
    extensionContext = context;
    console.log('DhrLang extension is now active!');

    // Create diagnostic collection for the fallback error-squiggle path
    // (only used when the Language Server can't start — see startLanguageClient).
    diagnosticCollection = vscode.languages.createDiagnosticCollection('dhrlang');
    context.subscriptions.push(diagnosticCollection);

    // Create output channel
    outputChannel = vscode.window.createOutputChannel('DhrLang');
    context.subscriptions.push(outputChannel);

    // Register commands
    const runCommand = vscode.commands.registerCommand('dhrlang.runFile', () => {
        runDhrLangFile();
    });

    const compileCommand = vscode.commands.registerCommand('dhrlang.compileFile', () => {
        compileDhrLangFile();
    });

    const helpCommand = vscode.commands.registerCommand('dhrlang.showHelp', () => {
        showDhrLangHelp();
    });

    const auditCommand = vscode.commands.registerCommand('dhrlang.auditContract', () => {
        auditContractFile();
    });

    const compileEvmCommand = vscode.commands.registerCommand('dhrlang.compileEvm', () => {
        compileEvmFile();
    });

    const gasCommand = vscode.commands.registerCommand('dhrlang.estimateGas', () => {
        estimateGas();
    });

    context.subscriptions.push(runCommand, compileCommand, helpCommand, auditCommand, compileEvmCommand, gasCommand);

    // Initialize status bar early
    ensureStatusBar();

    // Start the real DhrLang Language Server (go-to-definition, hover, rename,
    // find references, scope-aware completion, diagnostics). If Java or the
    // jar can't be found, fall back to the older in-process providers below
    // instead of crashing activation.
    startLanguageClient(context).then((started) => {
        if (!started) {
            registerFallbackProviders(context);
        }
    });
}

/**
 * Resolves java/jar the same way the run/compile commands do, then spawns
 * `java -jar <DhrLang.jar> --lsp` and connects a LanguageClient to it over
 * stdio. Returns true if the client was started, false if it couldn't be
 * (missing Java/jar) — callers should register the fallback providers in
 * that case so the extension still degrades gracefully.
 */
async function startLanguageClient(context: vscode.ExtensionContext): Promise<boolean> {
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        outputChannel.appendLine('[DhrLang LSP] DhrLang.jar not found — falling back to basic in-editor completion/diagnostics. Set dhrlang.jarPath or enable autoDetectJar.');
        return false;
    }

    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');

    const serverOptions: ServerOptions = {
        command: javaPath,
        args: ['-jar', jarResolved, '--lsp'],
        transport: TransportKind.stdio
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'dhrlang' }],
        outputChannel,
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.dhr')
        }
    };

    languageClient = new LanguageClient(
        'dhrlangLanguageServer',
        'DhrLang Language Server',
        serverOptions,
        clientOptions
    );

    try {
        await languageClient.start();
        context.subscriptions.push({ dispose: () => languageClient?.stop() });
        outputChannel.appendLine(`[DhrLang LSP] Started: ${javaPath} -jar "${jarResolved}" --lsp`);
        return true;
    } catch (e: any) {
        outputChannel.appendLine(`[DhrLang LSP] Failed to start Language Server: ${e?.message ?? e}. Falling back to basic in-editor completion/diagnostics.`);
        vscode.window.showWarningMessage('DhrLang: could not start the Language Server (check Java is installed and on PATH). Falling back to basic completion/diagnostics.');
        languageClient = undefined;
        return false;
    }
}

/**
 * Older, weaker in-process completion/hover/diagnostics path. Only used when
 * the real Language Server (startLanguageClient) couldn't start, so the
 * extension is still useful without a working Java/jar setup.
 */
function registerFallbackProviders(context: vscode.ExtensionContext) {
    const config = vscode.workspace.getConfiguration('dhrlang');

    if (config.get<boolean>('enableAutoCompletion', true)) {
        const completionProvider = vscode.languages.registerCompletionItemProvider(
            'dhrlang',
            new DhrLangCompletionProvider(),
            '.',
            '('
        );
        context.subscriptions.push(completionProvider);
    }

    const hoverProvider = vscode.languages.registerHoverProvider('dhrlang', new DhrLangHoverProvider());
    context.subscriptions.push(hoverProvider);

    // Run diagnostics on save and on open
    if (config.get<boolean>('enableErrorSquiggles', true)) {
        context.subscriptions.push(
            vscode.workspace.onDidSaveTextDocument((doc) => {
                if (doc.languageId === 'dhrlang' || doc.fileName.endsWith('.dhr')) {
                    runDiagnostics(doc);
                }
            }),
            vscode.workspace.onDidOpenTextDocument((doc) => {
                if (doc.languageId === 'dhrlang' || doc.fileName.endsWith('.dhr')) {
                    runDiagnostics(doc);
                }
            }),
            vscode.workspace.onDidCloseTextDocument((doc) => {
                diagnosticCollection.delete(doc.uri);
            })
        );

        // Run diagnostics on any already-open .dhr files
        vscode.workspace.textDocuments.forEach((doc) => {
            if (doc.languageId === 'dhrlang' || doc.fileName.endsWith('.dhr')) {
                runDiagnostics(doc);
            }
        });
    }
}

export function deactivate(): Thenable<void> | undefined {
    return languageClient ? languageClient.stop() : undefined;
}

let statusItem: vscode.StatusBarItem | undefined;

async function ensureStatusBar() {
    if (!statusItem) {
        statusItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 10);
        statusItem.command = 'dhrlang.showHelp';
        statusItem.show();
    }
    const jar = await resolveJarPath();
    statusItem.text = jar ? 'DhrLang: JAR OK' : 'DhrLang: JAR MISSING';
    statusItem.tooltip = jar ? jar : 'Place DhrLang.jar in workspace root or set dhrlang.jarPath';
}

async function resolveJarPath(): Promise<string | null> {
    const cfg = vscode.workspace.getConfiguration('dhrlang');
    const explicit = (cfg.get<string>('jarPath', '') || '').trim();
    if (explicit) return explicit;
    if (!cfg.get<boolean>('autoDetectJar', true)) return null;
    const folders = vscode.workspace.workspaceFolders;
    if (!folders) return null;
    for (const f of folders) {
        const rootJar = vscode.Uri.joinPath(f.uri, 'DhrLang.jar');
        try { await vscode.workspace.fs.stat(rootJar); return rootJar.fsPath; } catch { /* ignore */ }
    const libPattern = new vscode.RelativePattern(f, 'lib/DhrLang-*.jar');
    const libJars = await vscode.workspace.findFiles(libPattern, undefined, 1);
        if (libJars.length) return libJars[0].fsPath;
    }

    // 3. Check bundled JAR inside the extension
    if (extensionContext) {
        const bundledJar = vscode.Uri.joinPath(extensionContext.extensionUri, 'compiler', 'DhrLang.jar');
        try {
            await vscode.workspace.fs.stat(bundledJar);
            return bundledJar.fsPath;
        } catch { /* ignore */ }
    }

    return null;
}

async function runDhrLangFile() {
    await ensureStatusBar();
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showErrorMessage('No DhrLang file is open!');
        return;
    }
    const document = editor.document;
    if (!document.fileName.endsWith('.dhr')) {
        vscode.window.showErrorMessage('Please open a .dhr file to run!');
        return;
    }
    await document.save();
    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        vscode.window.showErrorMessage('Cannot locate DhrLang.jar. Set dhrlang.jarPath or enable autoDetectJar.');
        return;
    }
    const javaCmd = javaPath.includes(' ') ? `"${javaPath}"` : javaPath;
    const cmd = `${javaCmd} -jar "${jarResolved}" "${document.fileName}"`;
    const terminal = vscode.window.createTerminal({ name: 'DhrLang Output', cwd: path.dirname(document.fileName) });
    terminal.show();
    terminal.sendText(cmd);
}

async function compileDhrLangFile() {
    await ensureStatusBar();
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showErrorMessage('No DhrLang file is open!');
        return;
    }
    const document = editor.document;
    if (!document.fileName.endsWith('.dhr')) {
        vscode.window.showErrorMessage('Please open a .dhr file to compile!');
        return;
    }
    await document.save();

    const diagnostics = await runDiagnostics(document);
    if (diagnostics.length === 0) {
        vscode.window.showInformationMessage('DhrLang: No errors found. Code is clean!');
    } else {
        const errorCount = diagnostics.filter(d => d.severity === vscode.DiagnosticSeverity.Error).length;
        const warnCount = diagnostics.filter(d => d.severity === vscode.DiagnosticSeverity.Warning).length;
        const parts: string[] = [];
        if (errorCount > 0) { parts.push(`${errorCount} error${errorCount > 1 ? 's' : ''}`); }
        if (warnCount > 0) { parts.push(`${warnCount} warning${warnCount > 1 ? 's' : ''}`); }
        vscode.window.showErrorMessage(`DhrLang: ${parts.join(', ')} found. Check the Problems panel.`);
    }
}

/**
 * Runs the DhrLang compiler in --json --check mode and parses the output
 * into VS Code diagnostics (error squiggles with hints).
 */
async function runDiagnostics(document: vscode.TextDocument): Promise<vscode.Diagnostic[]> {
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        // Can't run diagnostics without JAR — silently skip
        return [];
    }

    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const javaCmd = javaPath.includes(' ') ? `"${javaPath}"` : javaPath;
    const cmd = `${javaCmd} -jar "${jarResolved}" --json --check "${document.fileName}"`;

    const diagnostics: vscode.Diagnostic[] = [];

    try {
        const { stdout, stderr } = await execAsync(cmd, {
            cwd: path.dirname(document.fileName),
            encoding: 'utf8',
            timeout: 15000
        });

        // Try to parse JSON from stdout or stderr
        const jsonStr = (stdout || '').trim() || (stderr || '').trim();
        if (jsonStr) {
            const parsed = tryParseJson(jsonStr);
            if (parsed) {
                diagnostics.push(...extractDiagnostics(parsed, document));
            }
        }
    } catch (e: any) {
        // The compiler exits with code 65 on errors and still outputs JSON
        const output = (e.stdout || '').trim() || (e.stderr || '').trim();
        if (output) {
            const parsed = tryParseJson(output);
            if (parsed) {
                diagnostics.push(...extractDiagnostics(parsed, document));
            } else {
                // Fallback: parse plain-text error output
                diagnostics.push(...parsePlainTextErrors(output, document));
            }
        }
    }

    diagnosticCollection.set(document.uri, diagnostics);
    return diagnostics;
}

function tryParseJson(text: string): any | null {
    try {
        // The JSON might have program output before it — find the JSON object
        const idx = text.indexOf('{');
        if (idx >= 0) {
            return JSON.parse(text.substring(idx));
        }
    } catch { /* not valid JSON */ }
    return null;
}

function extractDiagnostics(json: any, document: vscode.TextDocument): vscode.Diagnostic[] {
    const diagnostics: vscode.Diagnostic[] = [];

    // Handle errors array
    if (json.errors && Array.isArray(json.errors)) {
        for (const err of json.errors) {
            const diag = createDiagnostic(err, vscode.DiagnosticSeverity.Error, document);
            if (diag) { diagnostics.push(diag); }
        }
    }

    // Handle warnings array
    if (json.warnings && Array.isArray(json.warnings)) {
        for (const warn of json.warnings) {
            const diag = createDiagnostic(warn, vscode.DiagnosticSeverity.Warning, document);
            if (diag) { diagnostics.push(diag); }
        }
    }

    return diagnostics;
}

function createDiagnostic(
    entry: any,
    severity: vscode.DiagnosticSeverity,
    document: vscode.TextDocument
): vscode.Diagnostic | null {
    // entry may have: message, line, column, code, hint
    const line = Math.max(0, (entry.line || 1) - 1);
    const col = Math.max(0, (entry.column || 1) - 1);

    // Try to highlight the relevant token/word on the error line
    let range: vscode.Range;
    try {
        const textLine = document.lineAt(line);
        const wordRange = document.getWordRangeAtPosition(new vscode.Position(line, col));
        range = wordRange || new vscode.Range(line, col, line, textLine.text.length);
    } catch {
        range = new vscode.Range(line, col, line, col + 1);
    }

    const message = entry.message || 'Unknown error';
    const diag = new vscode.Diagnostic(range, message, severity);

    // Set error code (e.g., DHR-E201)
    if (entry.code) {
        diag.code = entry.code;
    }

    diag.source = 'DhrLang';

    // Add hint as related information
    if (entry.hint) {
        diag.relatedInformation = [
            new vscode.DiagnosticRelatedInformation(
                new vscode.Location(document.uri, range),
                `Hint: ${entry.hint}`
            )
        ];
    }

    return diag;
}

/**
 * Fallback parser for plain-text compiler error output.
 * Matches patterns like: "[line 5] Error at 'x': message"
 * or "Error [DHR-E201] at line 5, column 10: message"
 */
function parsePlainTextErrors(text: string, document: vscode.TextDocument): vscode.Diagnostic[] {
    const diagnostics: vscode.Diagnostic[] = [];
    const lines = text.split('\n');

    for (const rawLine of lines) {
        const trimmed = rawLine.trim();
        if (!trimmed) { continue; }

        // Pattern 1: [line N] Error ... : message
        const m1 = trimmed.match(/\[line\s+(\d+)\]\s*(Error|Warning).*?:\s*(.*)/i);
        if (m1) {
            const line = Math.max(0, parseInt(m1[1]) - 1);
            const severity = m1[2].toLowerCase() === 'warning'
                ? vscode.DiagnosticSeverity.Warning
                : vscode.DiagnosticSeverity.Error;
            const msg = m1[3].trim();
            diagnostics.push(new vscode.Diagnostic(
                new vscode.Range(line, 0, line, 200),
                msg,
                severity
            ));
            continue;
        }

        // Pattern 2: Error [CODE] at line N, column M: message
        const m2 = trimmed.match(/(?:Error|Warning)\s*\[([^\]]+)\].*?line\s+(\d+).*?column\s+(\d+).*?:\s*(.*)/i);
        if (m2) {
            const code = m2[1];
            const line = Math.max(0, parseInt(m2[2]) - 1);
            const col = Math.max(0, parseInt(m2[3]) - 1);
            const msg = m2[4].trim();
            const diag = new vscode.Diagnostic(
                new vscode.Range(line, col, line, col + 10),
                msg,
                trimmed.toLowerCase().startsWith('warning') ? vscode.DiagnosticSeverity.Warning : vscode.DiagnosticSeverity.Error
            );
            diag.code = code;
            diag.source = 'DhrLang';
            diagnostics.push(diag);
        }
    }

    return diagnostics;
}

function showDhrLangHelp() {
    const panel = vscode.window.createWebviewPanel('dhrLangHelp', 'DhrLang Help', vscode.ViewColumn.Two, { enableScripts: true });
    panel.webview.html = getDhrLangHelpContent();
}

// ── Smart Contract Commands ──────────────────────────────────────────

async function auditContractFile() {
    const editor = vscode.window.activeTextEditor;
    if (!editor || !editor.document.fileName.endsWith('.dhr')) {
        vscode.window.showErrorMessage('Open a .dhr file to audit.');
        return;
    }
    await editor.document.save();
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        vscode.window.showErrorMessage('Cannot locate DhrLang.jar.');
        return;
    }
    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const javaCmd = javaPath.includes(' ') ? `"${javaPath}"` : javaPath;

    outputChannel.show(true);
    outputChannel.appendLine('═══ DhrLang Security Audit ═══');
    outputChannel.appendLine(`File: ${editor.document.fileName}`);
    outputChannel.appendLine('');

    try {
        const cmd = `${javaCmd} -jar "${jarResolved}" --audit "${editor.document.fileName}"`;
        const { stdout, stderr } = await execAsync(cmd, {
            cwd: path.dirname(editor.document.fileName),
            timeout: 30000
        });
        outputChannel.appendLine(stdout || stderr || 'No output');

        // Also run JSON mode to get structured findings
        const jsonCmd = `${javaCmd} -jar "${jarResolved}" --audit --json "${editor.document.fileName}"`;
        try {
            const jsonResult = await execAsync(jsonCmd, {
                cwd: path.dirname(editor.document.fileName),
                timeout: 30000
            });
            const jsonStr = (jsonResult.stdout || '').trim();
            if (jsonStr) {
                const parsed = tryParseJson(jsonStr);
                if (parsed && parsed.findings) {
                    const critical = parsed.findings.filter((f: any) => f.severity === 'CRITICAL').length;
                    const high = parsed.findings.filter((f: any) => f.severity === 'HIGH').length;
                    const medium = parsed.findings.filter((f: any) => f.severity === 'MEDIUM').length;
                    if (critical > 0 || high > 0) {
                        vscode.window.showWarningMessage(
                            `DhrLang Audit: ${critical} critical, ${high} high, ${medium} medium findings. Check Output panel.`
                        );
                    } else {
                        vscode.window.showInformationMessage(
                            `DhrLang Audit: ${parsed.findings.length} findings (no critical/high). Check Output panel.`
                        );
                    }
                }
            }
        } catch { /* JSON parse fallback — text output is already shown */ }
    } catch (e: any) {
        outputChannel.appendLine(e.stdout || e.stderr || e.message || 'Audit failed');
        vscode.window.showErrorMessage('DhrLang audit completed with warnings. See Output panel.');
    }
}

async function compileEvmFile() {
    const editor = vscode.window.activeTextEditor;
    if (!editor || !editor.document.fileName.endsWith('.dhr')) {
        vscode.window.showErrorMessage('Open a .dhr file to compile to EVM.');
        return;
    }
    await editor.document.save();
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        vscode.window.showErrorMessage('Cannot locate DhrLang.jar.');
        return;
    }
    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const javaCmd = javaPath.includes(' ') ? `"${javaPath}"` : javaPath;

    const cmd = `${javaCmd} -jar "${jarResolved}" --compile-evm "${editor.document.fileName}"`;
    const terminal = vscode.window.createTerminal({ name: 'DhrLang EVM', cwd: path.dirname(editor.document.fileName) });
    terminal.show();
    terminal.sendText(cmd);
}

async function estimateGas() {
    const editor = vscode.window.activeTextEditor;
    if (!editor || !editor.document.fileName.endsWith('.dhr')) {
        vscode.window.showErrorMessage('Open a .dhr contract file to estimate gas.');
        return;
    }
    await editor.document.save();
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        vscode.window.showErrorMessage('Cannot locate DhrLang.jar.');
        return;
    }
    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const javaCmd = javaPath.includes(' ') ? `"${javaPath}"` : javaPath;

    outputChannel.show(true);
    outputChannel.appendLine('═══ DhrLang Gas Estimation ═══');

    try {
        const cmd = `${javaCmd} -jar "${jarResolved}" contract gas "${editor.document.fileName}"`;
        const { stdout, stderr } = await execAsync(cmd, {
            cwd: path.dirname(editor.document.fileName),
            timeout: 15000
        });
        outputChannel.appendLine(stdout || stderr || 'No output');
    } catch (e: any) {
        outputChannel.appendLine(e.stdout || e.stderr || e.message || 'Gas estimation failed');
    }
}

function getDhrLangHelpContent(): string {
    return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"/><title>DhrLang Help</title>
<style>body{font-family:Segoe UI,Tahoma,Arial,sans-serif;line-height:1.5;padding:24px;max-width:900px;margin:0 auto;color:var(--vscode-editor-foreground);background:var(--vscode-editor-background);}h1{margin-top:0;}code,pre{font-family:Consolas,monospace;font-size:13px;}section{margin-bottom:28px;padding:16px;border-left:4px solid #ff6b35;background:var(--vscode-textBlockQuote-background);}h2{color:#ff6b35;margin-top:0;}table{border-collapse:collapse;width:100%;}th,td{border:1px solid var(--vscode-panel-border);padding:4px 8px;font-family:Consolas,monospace;font-size:12px;}th{background:#ff6b3522;text-align:left;} .badge{background:#ff6b35;color:#fff;padding:2px 6px;border-radius:4px;font-size:11px;margin-left:6px;}</style></head><body>
<h1>DhrLang Help</h1>
<p>Core syntax: entry point <code>static kaam main()</code>. Types: <code>num</code>, <code>duo</code>, <code>sab</code>, <code>kya</code>, <code>kaam</code>, <code>any</code>. Null: <code>null</code>.</p>
<section><h2>Keywords</h2><table><tr><th>Category</th><th>Keywords</th></tr><tr><td>Control</td><td><code>if</code> <code>else</code> <code>while</code> <code>for</code> <code>return</code> <code>break</code> <code>continue</code> <code>try</code> <code>catch</code> <code>finally</code> <code>throw</code></td></tr><tr><td>OOP</td><td><code>class</code> <code>static</code> <code>extends</code> <code>implements</code></td></tr><tr><td>Other</td><td><code>this</code> <code>super</code> <code>null</code></td></tr></table></section>
<section><h2>StdLib</h2><p><code>printLine</code>, <code>substring</code>, <code>replace</code>, <code>arrayFill</code>, <code>arraySlice</code>, <code>arrayIndexOf</code>, <code>range</code>, <code>charAt</code></p></section>
<section><h2>Example</h2><pre>class Main {\n    static kaam main(){\n        num n=3; kya ok=true;\n        while(n>0){ printLine(n); n=n-1; }\n    }\n}</pre></section>
<section><h2>Usage</h2><ul><li>Configure JAR path in Settings if not beside workspace.</li><li>Run: Command Palette → DhrLang: Run File.</li><li>Compile only: DhrLang: Compile File.</li></ul></section>
</body></html>`;
}

class DhrLangCompletionProvider implements vscode.CompletionItemProvider {
    public provideCompletionItems(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken,
        context: vscode.CompletionContext
    ): vscode.ProviderResult<vscode.CompletionItem[] | vscode.CompletionList> {
        const items: vscode.CompletionItem[] = [];

        const specs = [
            { label: 'class', kind: vscode.CompletionItemKind.Class, insert: 'class ${1:Name} {\n\t${2}\n}', detail: 'Define a class' },
            { label: 'static', kind: vscode.CompletionItemKind.Keyword, insert: 'static ', detail: 'Static member modifier' },
            { label: 'kaam', kind: vscode.CompletionItemKind.Keyword, insert: 'kaam ', detail: 'Void-like return type' },
            { label: 'num', kind: vscode.CompletionItemKind.TypeParameter, insert: 'num ${1:var} = ${2:0};', detail: 'Integer type' },
            { label: 'duo', kind: vscode.CompletionItemKind.TypeParameter, insert: 'duo ${1:var} = ${2:0.0};', detail: 'Floating point type' },
            { label: 'sab', kind: vscode.CompletionItemKind.TypeParameter, insert: 'sab ${1:var} = "${2:text}";', detail: 'String type' },
            { label: 'kya', kind: vscode.CompletionItemKind.TypeParameter, insert: 'kya ${1:flag} = ${2:true};', detail: 'Boolean type' },
            { label: 'any', kind: vscode.CompletionItemKind.TypeParameter, insert: 'any ${1:x};', detail: 'Untyped / wildcard' },
            { label: 'if', kind: vscode.CompletionItemKind.Keyword, insert: 'if (${1:cond}) {\n\t${2}\n}', detail: 'Conditional' },
            { label: 'else', kind: vscode.CompletionItemKind.Keyword, insert: 'else {\n\t${1}\n}', detail: 'Else branch' },
            { label: 'while', kind: vscode.CompletionItemKind.Keyword, insert: 'while (${1:cond}) {\n\t${2}\n}', detail: 'While loop' },
            { label: 'for', kind: vscode.CompletionItemKind.Keyword, insert: 'for (num ${1:i}=0; ${1:i} < ${2:n}; ${1:i} = ${1:i} + 1) {\n\t${3}\n}', detail: 'For loop' },
            { label: 'return', kind: vscode.CompletionItemKind.Keyword, insert: 'return ${1:value};', detail: 'Return statement' },
            { label: 'try', kind: vscode.CompletionItemKind.Keyword, insert: 'try {\n\t${1}\n} catch (${2:e}) {\n\t${3}\n}', detail: 'Exception handling' },
            { label: 'catch', kind: vscode.CompletionItemKind.Keyword, insert: 'catch (${1:e}) {\n\t${2}\n}', detail: 'Catch clause' },
            { label: 'finally', kind: vscode.CompletionItemKind.Keyword, insert: 'finally {\n\t${1}\n}', detail: 'Finally clause' },
            { label: 'printLine', kind: vscode.CompletionItemKind.Function, insert: 'printLine(${1:value});', detail: 'Output function' },
            { label: 'main', kind: vscode.CompletionItemKind.Function, insert: 'static kaam main(){\n\t${1}\n}', detail: 'Program entry point' }
        ];

        specs.forEach(s => {
            const it = new vscode.CompletionItem(s.label, s.kind);
            it.detail = s.detail;
            it.insertText = new vscode.SnippetString(s.insert);
            it.documentation = new vscode.MarkdownString('`' + s.label + '` - ' + s.detail);
            items.push(it);
        });

        return items;
    }
}

class DhrLangHoverProvider implements vscode.HoverProvider {
    public provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.Hover> {
        
        const range = document.getWordRangeAtPosition(position);
        if (!range) {
            return;
        }

        const word = document.getText(range);
        
        const hoverInfo: { [key: string]: string } = {
            'class': 'Class declaration. Example: `class Box { num v; }`',
            'static': 'Static member belongs to class rather than an instance.',
            'kaam': 'Void-like return type (no value).',
            'num': 'Integer number type. Example: `num x = 1;`',
            'duo': 'Floating point type. Example: `duo y = 1.5;`',
            'sab': 'String type. Example: `sab s = "hi";`',
            'kya': 'Boolean type. Example: `kya ok = true;`',
            'any': 'Wildcard / any type.',
            'if': 'Conditional branch. `if (kyaExpr) { ... }`',
            'else': 'Else branch for preceding if.',
            'while': 'While loop. `while(cond){...}`',
            'for': 'Counting loop. `for(num i=0; i<n; i=i+1){...}`',
            'return': 'Return from a function / method.',
            'try': 'Start of exception handling block.',
            'catch': 'Exception capture block.',
            'finally': 'Always executed cleanup block.',
            'printLine': 'Output builtin. Example: `printLine(value);`',
            'main': 'Program entry point signature: `static kaam main()`'
        };

        if (hoverInfo[word]) {
            const markdown = new vscode.MarkdownString();
            markdown.appendMarkdown(`**${word}**\n\n${hoverInfo[word]}`);
            markdown.isTrusted = true;
            return new vscode.Hover(markdown);
        }

        return;
    }
}