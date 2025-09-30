# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]
_No unreleased changes yet_

## [1.1.3] - Unreleased

### Added
- CLI flags: `--help`, `--version`, improved `--json` path (structured usage output).
- CLI smoke tests (`CliSmokeTest`) exercising flags & JSON diagnostics.
- Diagnostics JSON schema file (`diagnostics.schema.json`).
- `--time` phase timing support (lex/parse/type/exec) with merged JSON object (`schemaVersion`=1).
- `--no-color` flag to disable ANSI sequences for CI/plain log environments.

### Planned (Not Implemented Yet)
- `--time` phase timing output.
- `--no-color` ANSI suppression.

## [1.0.3] - 2025-09-28
## [1.0.4] - 2025-09-28
## [1.0.5] - 2025-09-28
## [1.0.6] - 2025-09-28
## [1.0.7] - 2025-09-29
## [1.0.8] - 2025-09-29

### Changed
- Rewrote `EXAMPLES.md` calculator & removed Hindi keyword-based legacy examples; added minimal modern examples.
- Updated `GETTING_STARTED.md` to clarify English-core tokens and adjust control-flow/error handling sections.
- Updated `INSTALL.md` quick init command to use valid syntax (`class Main { static kaam main() { ... } }`).
- Cleaned VS Code snippets: removed unsupported Hindi keyword bodies & switch; added entry class, printLine, init pattern.

### Removed
- Snippet bilingual prefixes & legacy Hindi keyword constructs (मुख्य, प्रिंट, अगर, जबकि, के लिए, switch Hindi forms).
- Legacy bilingual calculator example with Java interop and Hindi keywords.

### Added
- Experimental placeholders for try/catch blocks labeled clearly.
- Simplified Hello World and array/OOP examples matching implemented feature set.

### Changed
- TUTORIALS.md rewritten to reflect actual implemented syntax (removed unsupported Hindi keywords & Java-only libraries; added accurate primitives, arrays, OOP, built-ins, experimental disclaimers).

### Removed
- Legacy tutorial sections relying on Java collections, StringBuilder, advanced exceptions, switch-case, static init blocks (unimplemented or unstable).

### Added
- Quick reference table, clarified best practices, experimental placeholders for generics & errors.

### Fixed
- Homebrew formula job failed (404) when downloading JAR from release; workflow now uses build artifact transfer instead of immediate release download to compute checksum.

### Changed
- Added debug listing of release directory and artifact upload step for reliability.

### Fixed
- GitHub Packages publish failing with 422 Unprocessable Entity: switched Maven `artifactId` to lowercase `dhrlang` and bumped version.

### Changed
- Version bumped to 1.0.5 to retry package publication.

### Fixed
- GitHub Packages publishing failed due to unset credentials; build now falls back to `gpr.user/gpr.key` Gradle props, then `GPR_USER/GPR_TOKEN`, then `GITHUB_ACTOR/GITHUB_TOKEN` (Actions default), finally `USERNAME/TOKEN`.

### Changed
- Version bumped to 1.0.4 to re-trigger release after credential fix.

### Fixed
- Release workflow: robust artifact discovery (fallback when *-all.jar naming differs) and proper tag version parsing (strip leading 'v').
- Distribution archives now reliably built (added distZip/distTar to release build step) preventing missing `build/distributions/*.zip` errors.
- Core version alignment with latest tag sequence; preparing for next feature iteration.

### Changed
- Internal CI: simplified release notes generation and consistent version propagation to publish task.

## [1.1.0] - 2025-09-28

### Changed
- VS Code extension: grammar overhauled to reflect current core language tokens (num, duo, sab, kya, kaam, any) and remove obsolete Hindi-only keyword set.
- Completion provider rebuilt with modern snippet set (class/static kaam main(), primitives, control flow, printLine, exception handling blocks).
- Hover help content updated to concise spec-aligned descriptions.
- Help webview replaced with streamlined HTML reflecting actual entry point and stdlib functions.

### Added
- Built-in function highlighting (printLine, substring, replace, arrayFill, arraySlice, arrayIndexOf, range, charAt).

### Removed
- Legacy Hindi keyword completions and highlighting (अगर, जबकि, आदि) to prevent confusion with unsupported syntax in the compiler.

## [1.1.2] - 2025-09-29

### Changed
- VS Code extension `package.json` metadata: clarified description to emphasize English-core tokens; pruned outdated Hindi-focused keywords.
- Extension README fully rewritten to match current language syntax (num/duo/sab/kya/kaam) and modern snippet set; removed legacy Hindi keyword tables.
- Bumped extension package version to 1.1.2 (was 1.1.1) in preparation for repackaging / publish.

### Removed
- Stale VSIX usage instructions referencing `dhrlang-vscode-1.0.0.vsix`; replaced with guidance aligned with new version.

### Added
- Packaging / publishing guidance (vsce package & publish steps) and troubleshooting matrix in extension README.

## [1.0.0] - 2025-09-23

### Added
- Generics support with type parameter substitution and strong diagnostics across fields and methods.
- Multi-dimensional arrays: parsing, type checking, evaluation, and tests (creation, indexing, bounds, and type rules).
- Implicit field access in instance methods: unqualified identifiers resolve to fields when no local/param matches, with access control and generic substitution.
- Compile-time checks for static field initializers:
  - STATIC_FORWARD_REFERENCE: same-class static forward reads are rejected.
  - STATIC_INIT_CYCLE: cycles among same-class static field initializers are rejected.
- Expanded diagnostics documentation (ERROR_CODES.md) and a Diagnostics Quick Guide in README.

### Changed
- SPEC.md updated to reflect generics substitution, multi-dimensional arrays, and implicit field access; clarified scoping rules for static vs instance contexts.
- README.md updated with new feature status and examples.

### Fixed
- Regression in undefined-variable hint preserved for static contexts while enabling implicit field access in instance methods.
- Improved static dependency analysis to catch forward references inside nested expressions and multi-dimensional initializers.

