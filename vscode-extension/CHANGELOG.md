# Changelog

All notable changes to the DhrLang VS Code extension are documented in this file.

The format is based on Keep a Changelog and the extension follows Semantic Versioning.

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
