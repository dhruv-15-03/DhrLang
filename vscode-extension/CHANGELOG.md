# Changelog

All notable changes to the DhrLang VS Code extension are documented in this file.

The format is based on Keep a Changelog and the extension follows Semantic Versioning.

## [4.0.2] - 2026-09-05

### Fixed
- Pinned `typeRoots` in `tsconfig.json` to the extension's own
  `node_modules/@types` and declared the two type packages actually used.
  `tsc` walks parent directories looking for `@types`, so packages installed
  above the checkout were pulled into the build and failed the local compile
  with errors from `@types/jest` and `@types/react-dom`, neither of which this
  extension depends on. CI never reproduced it, because a fresh runner has
  nothing above the checkout, so the build was green in Actions and broken on a
  developer machine.

### Changed
- Published to the VS Code Marketplace again. The release workflow triggers on
  `vscode-v*` tags and no such tag had been pushed since 3.x, so the Marketplace
  served 3.0.1 while this package was at 4.0.1.

## [4.0.1] - 2026-07-27

### Changed
- Aligned the VSIX version with the DhrLang release version so GitHub release assets are
  immediately identifiable as belonging to the same release.

## [3.1.0] - 2026-07-12

### Added
- **Wired the extension to the real DhrLang Language Server.** The extension now spawns
  `java -jar DhrLang.jar --lsp` and connects a `vscode-languageclient` `LanguageClient` over
  stdio, reusing the existing `dhrlang.jarPath` / `dhrlang.javaPath` / `dhrlang.autoDetectJar`
  resolution. This makes every LSP feature previously built server-side actually available in
  the editor for the first time: go-to-definition, find-all-references, hover, rename
  (`prepareRename` + rename), scope-aware completion, and `textDocument/publishDiagnostics`
  with full-span ranges and `DHR-Exxx` codes.
- Graceful fallback: if Java or `DhrLang.jar` can't be found (or the Language Server fails to
  start), the extension logs a clear message to the "DhrLang" output channel, shows a
  non-blocking warning, and falls back to the previous static keyword-completion/hover and
  shell-out diagnostics path instead of crashing activation.

### Changed
- `dhrlang.enableAutoCompletion` and `dhrlang.enableErrorSquiggles` now only govern the
  fallback path; they have no effect once the real Language Server is running.

## [3.0.1] - 2026-06-14

### Fixed
- Restored the real extension icon in published builds. The release workflow was
  overwriting the committed `images/icon.png` with a placeholder at build time, so the
  Marketplace listing could ship without its icon. The packaged VSIX now bundles the
  correct 1.45 MB icon.
- Hardened the automated release pipeline so tagging `vscode-vX.Y.Z` reliably packages
  and publishes to the Marketplace (a failed publish now fails the run instead of being
  silently swallowed).

> No functional changes to syntax highlighting, snippets, or commands in this release.

## [3.0.0] - 2026-04-25

### Added
- Updated TextMate grammar for the v3.0.0 token set: `do`, `switch`, `case`, `default`,
  `as`, and `emit` keywords with refreshed highlighting.
- Snippets for the main class, methods, loops, conditionals, and `printLine`.
- Run and compile commands (`Ctrl+F5` / `Ctrl+Shift+B`).
- Hover information and basic diagnostics surfacing compiler output.
- Automatic `DhrLang.jar` detection with configurable settings.

### Changed
- Documentation and grammar now reflect the current English-core keyword set
  (`num`, `duo`, `sab`, `kya`, `ek`, `kaam`); legacy Hindi keyword forms are no longer
  part of the advertised grammar.
