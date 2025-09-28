# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]
_No unreleased changes yet_

## [1.0.3] - 2025-09-28
## [1.0.4] - 2025-09-28

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

