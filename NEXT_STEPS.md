# DhrLang Next Steps

DhrLang is shipping. This document reflects the **current state (v3.0.0)** and the
realistic roadmap from here. For the detailed, iteration-by-iteration smart-contract /
agent / pipeline plan see `FUTURE_ENHANCEMENTS.md`.

## Where we are today (v3.0.0)

DhrLang's 3rd major release is live. The core language, three backends (AST / IR /
bytecode), the EVM smart-contract path, tooling, and distribution are all shipped.

### Shipped

- **Language core** — `num`, `duo`, `sab`, `kya`, `ek`, `kaam`; classes, generics,
  exceptions, labeled `break`/`continue`, `do`/`while`/`for`, `switch`/`case`/`default`.
- **v3.0.0 features** — `as` type casts (`as num` / `as duo` / `as sab`), hex literals,
  string interpolation (`"Hello ${name}!"`), bitwise operators (`& | ^ ~ << >>`).
- **EVM backend (`evm/`)** — SafeMath overflow protection, access-control codegen,
  collision-safe reentrancy lock, peephole optimizer, gas + memory tracking, ABI return
  encoding.
- **Debugging & tooling (`debug/`, `lsp/`)** — `inspect()`, `trace()`, gas profiling,
  call-graph + storage-layout visualizers, and a full stdio LSP server (`--lsp`).
- **Agent + pipeline runtimes (`agent/`, `pipeline/`)** — orchestration and data-pipeline
  foundations are implemented in `src/` (these were earlier planned as P2/P3).
- **Documentation** — `GETTING_STARTED.md`, `INSTALL.md`, `TUTORIALS.md`, `EXAMPLES.md`,
  `STDLIB.md`, `SPEC.md`, plus a refreshed `README.md`.
- **Automated releases**
  - JVM release pipeline on `v[0-9]*` tags (cross-platform JARs + checksums, GitHub
    Release).
  - VS Code extension pipeline on `vscode-v*` tags: packages with `vsce` and publishes to
    the Marketplace (publisher `EnggWithDhruv`). The extension is live at **v3.0.1**.

### Recently fixed

- **Numeric `as` casts** — `expr as num` / `expr as duo` (and `toNum` / `toDuo`) now accept
  numeric operands, not just strings. `(7 / 2) as num` is the supported integer-division
  idiom (truncates toward zero).
- **Extension release pipeline** — the build no longer clobbers the committed icon, and
  `vscode-*` tags no longer trigger the JVM CI/Release workflows.

## Immediate next steps

### 1. Language ergonomics

- **First-class integer division** (`//` operator or an `idiv`-style form). Today integer
  division is only reachable via the `(a / b) as num` workaround.
- **Better diagnostics** — improve the misleading "Cannot return 'T' from a function
  expecting 'kaam'" message when a return type is omitted.
- **Optional checked arithmetic for `num`** — core `num` currently wraps silently on
  overflow (two's-complement), which is at odds with the safety positioning the EVM side
  already enforces via SafeMath.

### 2. Build & contributor experience

- **Toolchain pinned to JDK 17** — `build.gradle` now declares a Java 17 toolchain and
  `settings.gradle` enables the foojay resolver, so the project compiles/tests against
  Java 17 regardless of the contributor's default JDK.
- **Modernize the Gradle wrapper** — Gradle 8.13 cannot *run* under newer JDKs (e.g. JDK
  25). Upgrading the wrapper (and the Shadow plugin to its Gradle-9-compatible fork) would
  let contributors build on the latest JDKs. This is a larger, higher-risk change tracked
  separately.

### 3. Distribution & reach

- **Homebrew tap — done.** [`dhruv-15-03/homebrew-dhrlang`](https://github.com/dhruv-15-03/homebrew-dhrlang)
  is live with a formula for the latest release (`brew tap dhruv-15-03/dhrlang && brew
  install dhrlang`). Bump `Formula/dhrlang.rb`'s `version`/`sha256` on every release.
- Chocolatey, Docker Hub, Snap Store — package configs exist in
  `.github/workflows/distribution.yml` but still need real publishing credentials
  (`DOCKER_USERNAME`/`DOCKER_PASSWORD` secrets, a Chocolatey API key, a Snap Store login)
  before they reach their respective stores.
- Online playground / REPL for zero-install trials.
- A documentation website built from the existing Markdown guides.

## How to cut a release

- **Language / JVM:** bump `version` in `build.gradle`, update `CHANGELOG.md`, then push a
  `vX.Y.Z` tag (matches the `v[0-9]*` trigger).
- **VS Code extension:** bump `vscode-extension/package.json`, add a
  `vscode-extension/CHANGELOG.md` entry, then push a `vscode-vX.Y.Z` tag. The workflow
  packages and publishes to the Marketplace automatically (requires the `VSCE_PAT` repo
  secret; regenerate it at dev.azure.com if a publish fails on auth).

## Getting involved

- **Good first issues:** documentation, example programs, additional snippets.
- **Intermediate:** language features (integer division, diagnostics), stdlib functions.
- **Advanced:** compiler optimizations, additional backends, IDE integrations.

Issues: https://github.com/dhruv-15-03/DhrLang/issues
Discussions: https://github.com/dhruv-15-03/DhrLang/discussions
