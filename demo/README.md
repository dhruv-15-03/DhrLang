# DhrLang LSP Demo

This is a real, reproducible transcript of the DhrLang Language Server
(`dhrlang.lsp.DhrLangLspServer`, launched via `java -jar DhrLang.jar --lsp`)
answering the exact JSON-RPC requests a VS Code editor sends while a
developer works on a `.dhr` file. Every request/response pair below was
captured by actually running the server against [`Demo.dhr`](./Demo.dhr) —
nothing here is hand-written or fabricated.

**Status:** the VS Code extension (`vscode-extension/`, v3.x, publisher
`EnggWithDhruv`) wires a real `vscode-languageclient` to this server and is
**CI-proven** — a headless VS Code integration test
(`vscode-extension/src/test/suite/extension.test.ts`, run in the
`vscode-lsp-integration` CI job) launches the extension, opens a `.dhr` file,
and asserts the client reaches `State.Running` and that completions round-trip
through the real server. The extension is **not currently published to the
VS Code Marketplace** — packaging is turnkey (`vsce package` produces an
installable `.vsix`), but publishing requires the `EnggWithDhruv` publisher's
Azure DevOps PAT, which is a human/maintainer step.

## Reproduce it yourself

```powershell
./gradlew shadowJar        # or your usual build task that produces build/libs/DhrLang.jar
pwsh demo/run-demo.ps1
```

`run-demo.ps1` frames the requests in [`requests.jsonl`](./requests.jsonl)
with LSP `Content-Length` headers, pipes them into `java -jar DhrLang.jar
--lsp` over stdio, and prints the raw responses — the same protocol
`vscode-languageclient` speaks with the server inside the editor.

## The fixture

[`Demo.dhr`](./Demo.dhr) — a small, always-syntactically-valid class
hierarchy used for every request below:

```dhrlang
class Point {
  num x;
  num y;
  kaam distance() { return 0; }
}

class Shape extends Point {
  num area;
}

class Main {
  static kaam main() {
    Shape s = new Shape();
    num total = 1;
    s.distance();

  }
}
```

## 1. Diagnostics (`textDocument/publishDiagnostics`)

Opening the fixture triggers the server's diagnostics pass (lexer → parser →
type checker) automatically. This fixture happens to contain a real type
error and a real unused-variable warning, so the transcript shows both:

```json
{
  "method": "textDocument/publishDiagnostics",
  "params": {
    "uri": "file:///demo.dhr",
    "diagnostics": [
      {
        "range": { "start": { "line": 3, "character": 20 }, "end": { "line": 3, "character": 26 } },
        "severity": 1,
        "source": "dhrlang",
        "code": "DHR-E201",
        "message": "Cannot return 'num' from a function expecting 'kaam'."
      },
      {
        "range": { "start": { "line": 13, "character": 8 }, "end": { "line": 13, "character": 13 } },
        "severity": 2,
        "source": "dhrlang",
        "code": "DHR-W001",
        "message": "Variable 'total' declared but never used."
      }
    ]
  }
}
```

This proves diagnostics fire with real `DHR-Exxx`/`DHR-Wxxx` codes, precise
ranges, and severities (1 = error, 2 = warning) — this is exactly what
renders as red/yellow squiggles in the editor.

## 2. Hover (`textDocument/hover`)

Request: line 6, character 20 — the `Point` in `class Shape extends Point`.

```json
{ "id": 2, "result": { "contents": { "kind": "markdown", "value": "**Point** — `class`\n\n" } } }
```

## 3. Go to definition (`textDocument/definition`)

Request: line 12, character 18 — the `Shape` in `new Shape()`.

```json
{
  "id": 3,
  "result": {
    "uri": "file:///demo.dhr",
    "range": { "start": { "line": 6, "character": 6 }, "end": { "line": 6, "character": 11 } }
  }
}
```

Jumps straight to `class Shape` on line 6 (0-based).

## 4. Find all references (`textDocument/references`)

Request: line 0, character 6 — the `Point` class declaration name,
`includeDeclaration: true`.

```json
{
  "id": 4,
  "result": [
    { "uri": "file:///demo.dhr", "range": { "start": { "line": 0, "character": 6 }, "end": { "line": 0, "character": 11 } } },
    { "uri": "file:///demo.dhr", "range": { "start": { "line": 6, "character": 20 }, "end": { "line": 6, "character": 25 } } }
  ]
}
```

Both usages found: the declaration itself and the `extends Point` reference.

## 5. Rename (`textDocument/prepareRename` + `textDocument/rename`)

Request: line 6, character 6 — the `Shape` declaration name, renaming to
`Rectangle`.

`prepareRename` confirms the symbol at the cursor and its current text:

```json
{ "id": 5, "result": { "range": { "start": { "line": 6, "character": 6 }, "end": { "line": 6, "character": 11 } }, "placeholder": "Shape" } }
```

`rename` returns a `WorkspaceEdit` updating all three occurrences (the
declaration, the local variable type, and the constructor call):

```json
{
  "id": 6,
  "result": {
    "changes": {
      "file:///demo.dhr": [
        { "range": { "start": { "line": 6,  "character": 6  }, "end": { "line": 6,  "character": 11 } }, "newText": "Rectangle" },
        { "range": { "start": { "line": 12, "character": 4  }, "end": { "line": 12, "character": 9  } }, "newText": "Rectangle" },
        { "range": { "start": { "line": 12, "character": 18 }, "end": { "line": 12, "character": 23 } }, "newText": "Rectangle" }
      ]
    }
  }
}
```

## 6. General scope-aware completion (`textDocument/completion`)

Request: line 15, character 0 — the blank line at the end of `main()`'s body.

The result (truncated here — see [`responses excerpt`](#reproduce-it-yourself)
for the full payload) includes **locals in scope** (`s: Shape`,
`total: num`), the **enclosing method** (`main`), **sibling class names**
(`Point`, `Shape`, `Main`), and the language's keywords/types/annotations —
everything a developer could type at that cursor position, not a flat
unfiltered dump:

```json
{
  "id": 7,
  "result": [
    { "label": "s",     "kind": 6, "detail": "Shape" },
    { "label": "total", "kind": 6, "detail": "num" },
    { "label": "main",  "kind": 2, "detail": "kaam main(...)" },
    { "label": "Point", "kind": 7, "detail": "class" },
    { "label": "Shape", "kind": 7, "detail": "class" },
    { "label": "Main",  "kind": 7, "detail": "class" }
  ]
}
```

## 7. Member-access / dot completion (`textDocument/completion`)

Request: line 14, character 6 — immediately after the `.` in `s.distance();`
(re-triggering completion mid-expression, as a `Ctrl+Space` would).

```json
{
  "id": 8,
  "result": [
    { "label": "area",     "kind": 5, "detail": "num" },
    { "label": "x",        "kind": 5, "detail": "num" },
    { "label": "y",        "kind": 5, "detail": "num" },
    { "label": "distance", "kind": 2, "detail": "kaam distance(...)" }
  ]
}
```

Only `Shape`'s own field (`area`) and its inherited members from `Point`
(`x`, `y`, `distance`) are offered — no locals (`total`), no keywords, no
unrelated classes. This is the scope-aware, receiver-typed completion added
in PR #21, proven here against the real compiled server.

---

Every feature above is driven entirely by the same AST/symbol-table
infrastructure (`Parser`, `TypeChecker`, and the LSP's scope index) that
backs the compiler itself — this is one implementation serving both the
`dhrc` compiler and the editor, not a separate toy analyzer.
