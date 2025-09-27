import * as vscode from 'vscode';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

export function activate(context: vscode.ExtensionContext) {
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
        'DhrLang extension activated! हिंदी प्रोग्रामिंग के लिए तैयार है।',
        'Show Help'
    ).then(selection => {
        if (selection === 'Show Help') {
            showDhrLangHelp();
        }
    });
}

export function deactivate() {
    console.log('DhrLang extension deactivated');
}

async function runDhrLangFile() {
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

    // Save the file first
    await document.save();

    const config = vscode.workspace.getConfiguration('dhrlang');
    const javaPath = config.get<string>('javaPath', 'java');
    const jarPath = config.get<string>('jarPath', '');

    let command: string;
    if (jarPath && jarPath.trim() !== '') {
        command = `"${javaPath}" -jar "${jarPath}" "${document.fileName}"`;
    } else {
        // Try to find DhrLang.jar in common locations or use default
        command = `"${javaPath}" -jar DhrLang.jar "${document.fileName}"`;
    }

    // Create and show terminal
    const terminal = vscode.window.createTerminal({
        name: 'DhrLang Output',
        cwd: path.dirname(document.fileName)
    });

    terminal.show();
    terminal.sendText(command);
}

async function compileDhrLangFile() {
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
    const jarPath = config.get<string>('jarPath', '');

    let command: string;
    if (jarPath && jarPath.trim() !== '') {
        command = `"${javaPath}" -jar "${jarPath}" --check "${document.fileName}"`;
    } else {
        command = `"${javaPath}" -jar DhrLang.jar --check "${document.fileName}"`;
    }

    try {
        const { stdout, stderr } = await execAsync(command, { 
            cwd: path.dirname(document.fileName),
            encoding: 'utf8'
        });

        if (stderr) {
            vscode.window.showErrorMessage(`Compilation Error: ${stderr}`);
        } else {
            vscode.window.showInformationMessage('✅ DhrLang file compiled successfully!');
            if (stdout.trim()) {
                const outputChannel = vscode.window.createOutputChannel('DhrLang');
                outputChannel.appendLine('=== DhrLang Compilation Output ===');
                outputChannel.appendLine(stdout);
                outputChannel.show();
            }
        }
    } catch (error: any) {
        vscode.window.showErrorMessage(`Compilation failed: ${error.message}`);
    }
}

function showDhrLangHelp() {
    const panel = vscode.window.createWebviewPanel(
        'dhrLangHelp',
        'DhrLang Help - सहायता',
        vscode.ViewColumn.Two,
        {
            enableScripts: true
        }
    );

    panel.webview.html = getDhrLangHelpContent();
}

function getDhrLangHelpContent(): string {
    return `
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>DhrLang Help</title>
        <style>
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                line-height: 1.6;
                color: var(--vscode-editor-foreground);
                background-color: var(--vscode-editor-background);
                padding: 20px;
                max-width: 800px;
                margin: 0 auto;
            }
            .header {
                text-align: center;
                margin-bottom: 30px;
                padding: 20px;
                background: linear-gradient(135deg, #FF6B35 0%, #F7931E 100%);
                color: white;
                border-radius: 10px;
            }
            .section {
                margin-bottom: 25px;
                padding: 15px;
                border-left: 4px solid #FF6B35;
                background-color: var(--vscode-textBlockQuote-background);
            }
            .keyword {
                background-color: var(--vscode-textPreformat-background);
                padding: 2px 6px;
                border-radius: 3px;
                font-family: 'Courier New', monospace;
                color: #FF6B35;
                font-weight: bold;
            }
            .example {
                background-color: var(--vscode-textCodeBlock-background);
                padding: 10px;
                border-radius: 5px;
                font-family: 'Courier New', monospace;
                margin: 10px 0;
                border: 1px solid var(--vscode-panel-border);
            }
            h2 {
                color: #FF6B35;
                border-bottom: 2px solid #FF6B35;
                padding-bottom: 5px;
            }
            .hindi {
                font-size: 1.1em;
                color: #4CAF50;
            }
            ul {
                list-style-type: none;
                padding-left: 0;
            }
            li {
                margin: 5px 0;
                padding: 5px 0;
                border-bottom: 1px dotted var(--vscode-panel-border);
            }
            .shortcut {
                float: right;
                background-color: var(--vscode-button-background);
                color: var(--vscode-button-foreground);
                padding: 2px 8px;
                border-radius: 3px;
                font-size: 0.9em;
            }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>🇮🇳 DhrLang Help - सहायता</h1>
            <p>Programming in Hindi - हिंदी में प्रोग्रामिंग</p>
        </div>

        <div class="section">
            <h2>📝 Basic Keywords - मूलभूत शब्द</h2>
            <ul>
                <li><span class="keyword">मुख्य()</span> - Main function <span class="hindi">(main function)</span></li>
                <li><span class="keyword">प्रिंट()</span> - Print statement <span class="hindi">(print statement)</span></li>
                <li><span class="keyword">अगर</span> - If condition <span class="hindi">(if condition)</span></li>
                <li><span class="keyword">नहीं तो</span> - Else <span class="hindi">(else)</span></li>
                <li><span class="keyword">जबकि</span> - While loop <span class="hindi">(while loop)</span></li>
                <li><span class="keyword">के लिए</span> - For loop <span class="hindi">(for loop)</span></li>
                <li><span class="keyword">वापसी</span> - Return <span class="hindi">(return)</span></li>
            </ul>
        </div>

        <div class="section">
            <h2>🔢 Data Types - डेटा प्रकार</h2>
            <ul>
                <li><span class="keyword">संख्या</span> - Integer <span class="hindi">(number/integer)</span></li>
                <li><span class="keyword">दशमलव</span> - Decimal/Float <span class="hindi">(decimal/float)</span></li>
                <li><span class="keyword">स्ट्रिंग</span> - String <span class="hindi">(string)</span></li>
                <li><span class="keyword">बूलियन</span> - Boolean <span class="hindi">(boolean)</span></li>
                <li><span class="keyword">चार</span> - Character <span class="hindi">(character)</span></li>
            </ul>
        </div>

        <div class="section">
            <h2>🏗️ OOP Keywords - OOP शब्द</h2>
            <ul>
                <li><span class="keyword">क्लास</span> - Class <span class="hindi">(class)</span></li>
                <li><span class="keyword">निजी</span> - Private <span class="hindi">(private)</span></li>
                <li><span class="keyword">संरक्षित</span> - Protected <span class="hindi">(protected)</span></li>
                <li><span class="keyword">सार्वजनिक</span> - Public <span class="hindi">(public)</span></li>
                <li><span class="keyword">स्टैटिक</span> - Static <span class="hindi">(static)</span></li>
            </ul>
        </div>

        <div class="section">
            <h2>🎯 Example Program - उदाहरण प्रोग्राम</h2>
            <div class="example">
// Simple DhrLang Program
मुख्य() {
    संख्या age = 25;
    स्ट्रिंग name = "राहुल";
    
    प्रिंट("नाम: " + name);
    प्रिंट("उम्र: " + age);
    
    अगर (age >= 18) {
        प्रिंट("आप वयस्क हैं!");
    } नहीं तो {
        प्रिंट("आप अभी बच्चे हैं!");
    }
}
            </div>
        </div>

        <div class="section">
            <h2>⌨️ Keyboard Shortcuts - कीबोर्ड शॉर्टकट</h2>
            <ul>
                <li>Run File - फ़ाइल चलाएं <span class="shortcut">Ctrl+F5</span></li>
                <li>Compile File - फ़ाइल कंपाइल करें <span class="shortcut">Ctrl+Shift+B</span></li>
                <li>Auto-completion - ऑटो-कंप्लीशन <span class="shortcut">Ctrl+Space</span></li>
            </ul>
        </div>

        <div class="section">
            <h2>🚀 Getting Started - शुरुआत करें</h2>
            <ol>
                <li>Create a new file with <code>.dhr</code> extension</li>
                <li>Type <code>main</code> and press Tab for main function template</li>
                <li>Write your DhrLang code using Hindi keywords</li>
                <li>Press <strong>Ctrl+F5</strong> to run your program</li>
                <li>Enjoy programming in Hindi! हिंदी में प्रोग्रामिंग का आनंद लें!</li>
            </ol>
        </div>

        <div class="section">
            <h2>🔗 Resources - संसाधन</h2>
            <ul>
                <li><a href="https://github.com/dhruv-15-03/DhrLang">GitHub Repository</a></li>
                <li><a href="https://github.com/dhruv-15-03/DhrLang/blob/main/TUTORIALS.md">Complete Tutorials</a></li>
                <li><a href="https://github.com/dhruv-15-03/DhrLang/blob/main/EXAMPLES.md">Code Examples</a></li>
                <li><a href="https://github.com/dhruv-15-03/DhrLang/issues">Report Issues</a></li>
            </ul>
        </div>
    </body>
    </html>
    `;
}

class DhrLangCompletionProvider implements vscode.CompletionItemProvider {
    public provideCompletionItems(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken,
        context: vscode.CompletionContext
    ): vscode.ProviderResult<vscode.CompletionItem[] | vscode.CompletionList> {
        
        const completionItems: vscode.CompletionItem[] = [];

        // Hindi keywords
        const hindiKeywords = [
            { label: 'मुख्य', detail: 'main function', insertText: 'मुख्य() {\n\t${1}\n}', kind: vscode.CompletionItemKind.Function },
            { label: 'प्रिंट', detail: 'print statement', insertText: 'प्रिंट("${1}");', kind: vscode.CompletionItemKind.Function },
            { label: 'अगर', detail: 'if condition', insertText: 'अगर (${1}) {\n\t${2}\n}', kind: vscode.CompletionItemKind.Keyword },
            { label: 'नहीं तो', detail: 'else', insertText: 'नहीं तो {\n\t${1}\n}', kind: vscode.CompletionItemKind.Keyword },
            { label: 'जबकि', detail: 'while loop', insertText: 'जबकि (${1}) {\n\t${2}\n}', kind: vscode.CompletionItemKind.Keyword },
            { label: 'के लिए', detail: 'for loop', insertText: 'के लिए (संख्या ${1:i} = 0; ${1:i} < ${2:10}; ${1:i}++) {\n\t${3}\n}', kind: vscode.CompletionItemKind.Keyword },
            { label: 'वापसी', detail: 'return statement', insertText: 'वापसी ${1};', kind: vscode.CompletionItemKind.Keyword },
            { label: 'संख्या', detail: 'integer type', insertText: 'संख्या ${1:variableName} = ${2:0};', kind: vscode.CompletionItemKind.TypeParameter },
            { label: 'दशमलव', detail: 'decimal type', insertText: 'दशमलव ${1:variableName} = ${2:0.0};', kind: vscode.CompletionItemKind.TypeParameter },
            { label: 'स्ट्रिंग', detail: 'string type', insertText: 'स्ट्रिंग ${1:variableName} = "${2:value}";', kind: vscode.CompletionItemKind.TypeParameter },
            { label: 'बूलियन', detail: 'boolean type', insertText: 'बूलियन ${1:variableName} = ${2:true};', kind: vscode.CompletionItemKind.TypeParameter },
            { label: 'चार', detail: 'character type', insertText: 'चार ${1:variableName} = \'${2:a}\';', kind: vscode.CompletionItemKind.TypeParameter },
            { label: 'क्लास', detail: 'class definition', insertText: 'क्लास ${1:ClassName} {\n\t${2}\n}', kind: vscode.CompletionItemKind.Class },
            { label: 'निजी', detail: 'private modifier', insertText: 'निजी ', kind: vscode.CompletionItemKind.Keyword },
            { label: 'संरक्षित', detail: 'protected modifier', insertText: 'संरक्षित ', kind: vscode.CompletionItemKind.Keyword },
            { label: 'सार्वजनिक', detail: 'public modifier', insertText: 'सार्वजनिक ', kind: vscode.CompletionItemKind.Keyword },
            { label: 'स्टैटिक', detail: 'static modifier', insertText: 'स्टैटिक ', kind: vscode.CompletionItemKind.Keyword },
            { label: 'कोशिश', detail: 'try block', insertText: 'कोशिश {\n\t${1}\n} पकड़ना (${2:Exception} ${3:e}) {\n\t${4}\n}', kind: vscode.CompletionItemKind.Keyword },
            { label: 'पकड़ना', detail: 'catch block', insertText: 'पकड़ना (${1:Exception} ${2:e}) {\n\t${3}\n}', kind: vscode.CompletionItemKind.Keyword },
            { label: 'अंततः', detail: 'finally block', insertText: 'अंततः {\n\t${1}\n}', kind: vscode.CompletionItemKind.Keyword }
        ];

        hindiKeywords.forEach(keyword => {
            const item = new vscode.CompletionItem(keyword.label, keyword.kind);
            item.detail = keyword.detail;
            item.insertText = new vscode.SnippetString(keyword.insertText);
            item.documentation = new vscode.MarkdownString(`**${keyword.label}** - ${keyword.detail}`);
            completionItems.push(item);
        });

        return completionItems;
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
            'मुख्य': 'Main function - प्रोग्राम का मुख्य भाग\n\nExample: `मुख्य() { ... }`',
            'प्रिंट': 'Print statement - आउटपुट प्रिंट करने के लिए\n\nExample: `प्रिंट("Hello World");`',
            'अगर': 'If condition - शर्त जांचने के लिए\n\nExample: `अगर (x > 0) { ... }`',
            'नहीं तो': 'Else statement - वैकल्पिक शर्त\n\nExample: `नहीं तो { ... }`',
            'जबकि': 'While loop - जब तक शर्त सत्य है\n\nExample: `जबकि (i < 10) { ... }`',
            'के लिए': 'For loop - निर्धारित संख्या में लूप\n\nExample: `के लिए (संख्या i = 0; i < 10; i++) { ... }`',
            'वापसी': 'Return statement - मान वापस करने के लिए\n\nExample: `वापसी result;`',
            'संख्या': 'Integer type - पूर्ण संख्या\n\nExample: `संख्या age = 25;`',
            'दशमलव': 'Decimal/Float type - दशमलव संख्या\n\nExample: `दशमलव price = 99.99;`',
            'स्ट्रिंग': 'String type - टेक्स्ट\n\nExample: `स्ट्रिंग name = "राहुल";`',
            'बूलियन': 'Boolean type - सत्य/असत्य\n\nExample: `बूलियन isActive = true;`',
            'चार': 'Character type - एक अक्षर\n\nExample: `चार grade = \'A\';`',
            'क्लास': 'Class definition - क्लास बनाने के लिए\n\nExample: `क्लास Student { ... }`',
            'निजी': 'Private access modifier - केवल इसी क्लास में उपलब्ध',
            'संरक्षित': 'Protected access modifier - इस क्लास और उसकी उप-क्लासों में उपलब्ध',
            'सार्वजनिक': 'Public access modifier - सभी जगह उपलब्ध',
            'स्टैटिक': 'Static modifier - क्लास स्तर पर उपलब्ध',
            'कोशिश': 'Try block - त्रुटि हैंडलिंग के लिए\n\nExample: `कोशिश { ... } पकड़ना { ... }`',
            'पकड़ना': 'Catch block - त्रुटि को पकड़ने के लिए',
            'अंततः': 'Finally block - हमेशा चलने वाला कोड'
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