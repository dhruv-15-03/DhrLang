import * as vscode from 'vscode';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

let extensionContext: vscode.ExtensionContext;

export function activate(context: vscode.ExtensionContext) {
    extensionContext = context;
    console.log('DhrLang extension is now active!');

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

    context.subscriptions.push(runCommand, compileCommand, helpCommand);

    // Initialize status bar early
    ensureStatusBar();

    // Register completion provider
    const completionProvider = vscode.languages.registerCompletionItemProvider(
        'dhrlang',
        new DhrLangCompletionProvider(),
        // Trigger completion on these characters
        '.',
        '('
    );

    context.subscriptions.push(completionProvider);

    // Register hover provider
    const hoverProvider = vscode.languages.registerHoverProvider('dhrlang', new DhrLangHoverProvider());
    context.subscriptions.push(hoverProvider);

    // Show welcome message on extension activation
    vscode.window.showInformationMessage(
        'DhrLang extension activated! (Spec: num/duo/sab/kya + class/static kaam main())',
        'Show Help'
    ).then(selection => {
        if (selection === 'Show Help') {
            showDhrLangHelp();
        }
    });
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
    const cmd = `"${javaPath}" -jar "${jarResolved}" "${document.fileName}"`;
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
    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const jarResolved = await resolveJarPath();
    if (!jarResolved) {
        vscode.window.showErrorMessage('Cannot locate DhrLang.jar. Set dhrlang.jarPath or enable autoDetectJar.');
        return;
    }
    const cmd = `"${javaPath}" -jar "${jarResolved}" --check "${document.fileName}"`;
    try {
        const { stdout, stderr } = await execAsync(cmd, { cwd: path.dirname(document.fileName), encoding: 'utf8' });
        if (stderr) {
            vscode.window.showErrorMessage(`Compilation Error: ${stderr}`);
        } else {
            vscode.window.showInformationMessage('DhrLang file compiled successfully');
            if (stdout.trim()) {
                const out = vscode.window.createOutputChannel('DhrLang');
                out.appendLine('=== Compilation Output ===');
                out.appendLine(stdout);
                out.show();
            }
        }
    } catch (e: any) {
        vscode.window.showErrorMessage(`Compilation failed: ${e.message}`);
    }
}

function showDhrLangHelp() {
    const panel = vscode.window.createWebviewPanel('dhrLangHelp', 'DhrLang Help', vscode.ViewColumn.Two, { enableScripts: true });
    panel.webview.html = getDhrLangHelpContent();
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