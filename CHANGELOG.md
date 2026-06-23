# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]

## [3.8.0] - 2026-06-25

### Added
- **`contract stdlib` - browse & scaffold the standard base-contract library.** A new
  subcommand exposes the eight OpenZeppelin-style base contracts that previously lived
  as dead code, turning them into a real, discoverable starting point for new contracts:
  - `dhrlang contract stdlib list` prints the catalog (Ownable, ReentrancyGuard,
    Pausable, SafeMath, ERC20, ERC721, AccessControl, TimelockController) with a
    one-line description for each.
  - `dhrlang contract stdlib show <Name>` prints a template's DhrLang source to stdout.
  - `dhrlang contract stdlib new <Name> [--name=<Custom>] [--output=<dir>]` scaffolds
    `<Name>.dhr` (or `<Custom>.dhr`, renaming the contract class) into the output
    directory, refusing to overwrite an existing file.

  Every template is validated by the build: `ContractStdlibTest` compiles all eight
  through the real Lexer -> Parser -> TypeChecker -> EVM pipeline and asserts a
  non-empty ABI, so the library can never silently rot again. The token templates
  (ERC20/ERC721) ship as honest **starter scaffolds** - transfer/balance bookkeeping is
  stubbed for you to fill in; Ownable, Pausable, ReentrancyGuard, SafeMath,
  AccessControl and TimelockController are complete patterns.

- **`address(num)` builtin - first-class numeric-to-Address cast.** DhrLang now
  understands `address(0)` (the zero address) and `address(n)` generally, lowering on
  the EVM to a 160-bit mask (`x AND 0xff..ff`). This closes a real language gap: there
  was previously no way to write a zero/sentinel address in source, which is exactly
  what owner-guard patterns (`if (newOwner == address(0)) { ... }`) need. The
  type-checker types `address(x)` as `Address`, requires a single numeric argument, and
  reports a clear error otherwise (`DHR-E601` arity / `DHR-E201` type mismatch).

### Notes
- Additive and non-breaking: existing contracts compile identically. The `address`
  builtin occupies a previously unused call name (lowercase `address`; the capitalised
  `Address` type keyword is unaffected).

### Added
- **`contract export` - framework-ready interop artifacts (Hardhat, Foundry,
  viem/wagmi).** A new subcommand that compiles every `@contract` in a source file
  and emits the artifacts the surrounding EVM ecosystem already knows how to consume,
  so a DhrLang contract drops straight into an existing JavaScript/TypeScript or
  Solidity-tooling project.
  - **Hardhat.** Writes `hardhat/<Name>.json` in the `hh-sol-artifact-1` shape
    (`_format`, `contractName`, `sourceName`, `compiler`, `abi`, `bytecode`,
    `deployedBytecode`, `linkReferences`), readable by Hardhat, ethers, viem and wagmi.
  - **Foundry.** Writes `foundry/<Name>.json` matching the shape `forge` emits under
    `out/` (`abi`, `bytecode.object`, `deployedBytecode.object`, `metadata`).
  - **viem / wagmi.** Writes `ts/<Name>.ts` exporting the ABI `as const` (the assertion
    that unlocks viem's compile-time type inference) plus `0x`-prefixed creation and
    runtime bytecode constants, and a `ts/index.ts` barrel re-exporting every module.
  - **Selectable output.** `--format=all|hardhat|foundry|ts` (default `all`) picks which
    targets to emit; `--output=<dir>` chooses the destination (default `build/contracts`).
    The same per-contract `AbiGenerator` selectors back both the ABI entries and the
    bytecode, so off-chain decoders line up with on-chain dispatch.

### Notes
- Additive, non-breaking. `export` is a pure, read-only projection of the compiled
  artifacts - it touches no codegen, audit or SARIF path, so existing behavior and the
  Security Audit alerts are unaffected. All generated output is plain ASCII for
  byte-for-byte deterministic artifacts across platforms.

## [3.6.0] - 2026-06-23

### Added
- **Smart-contract safety report with a CI gate (`contract safety`), provable
  safety layer L4.** A new subcommand that folds the L3 specification fuzzer into
  the static security audit and turns the result into a single, gradeable safety
  artifact.
  - **Unified analysis.** `dhrlang contract safety token.dhr` runs the full
    `AuditReportGenerator` deep analysis (reentrancy, `tx.origin`, taint-to-storage,
    arithmetic overflow, access-control and view/pure detectors) *and*, opt-in, the
    L3 fuzzer. Each invariant/postcondition counterexample the fuzzer finds is folded
    back in as a HIGH-severity `FUZZ-INVARIANT` (or `FUZZ-EXCEPTION`) finding carrying
    the **minimized counterexample**, so spec bugs and code smells surface in one place.
  - **Safety score + letter grade.** The report derives a `safetyScore`
    (`100 - riskScore`) and an A-F `safetyGrade` (A >= 90, B >= 75, C >= 60, D >= 40,
    F < 40), so a contract's posture is a single, glanceable number.
  - **Human + machine output.** Emits a GitHub-flavored **Markdown report** (score,
    severity table, per-contract table, findings + details) to stdout, or JSON with
    `--json`. It also writes `safety.sarif` (GitHub Code Scanning ingestible) and
    `safety-report.md` to the output directory for CI job summaries.
  - **CI gate.** `--fail-on=critical|high|medium|low|none` sets the severity threshold
    (default `high`); the command exits non-zero when any finding meets it, so
    `contract safety` drops straight into a pipeline. `--fail-on=none` disables the
    gate (report-only).

### Notes
- Additive, non-breaking. The existing `--audit`/`--sarif` path is unchanged, so the
  Security Audit workflow and its Code Scanning alerts are unaffected. Fuzzing inside
  the auditor is **opt-in** (off by default), so contracts without specs produce an
  identical audit to before.

## [3.5.0] - 2026-06-20

### Added
- **Specification fuzzing for smart contracts (`contract fuzz`), provable safety
  layer L3.** A new property-based fuzzer that searches for inputs which falsify a
  contract's declared `@ensures` postconditions and `@invariant` contract invariants.
  - **`SpecFuzzEngine`** — a sound, concrete `uint256` evaluator that executes a
    contract function over a simulated EVM state (`2^256` wrapping arithmetic,
    `@checked` overflow reverts, mapping/storage reads defaulting to zero) and then
    checks every applicable specification. It is deliberately **sound, not complete**:
    it only reports a `VIOLATION` when a faithful execution falsifies a spec; anything
    it cannot model faithfully (user-function calls, unsupported statements, non-numeric
    arguments) degrades to a skip, never a false positive.
  - **`ContractFuzzer`** is now backed by that engine (previously a stub). It generates
    randomized arguments, runs each fuzzable function, **minimizes** any failing input
    toward a smaller counterexample, and reports per-function `ok / violations / reverts
    / skipped / errors` tallies. A run is reproducible under `--seed`.
  - **CLI:** `dhrlang contract fuzz [--runs=N] [--seed=N] <file.dhr>` fuzzes every
    contract's specs and exits non-zero when a counterexample is found, so it doubles as
    a CI gate. `--runs` controls iterations per function (default 256); `--seed` makes a
    run deterministic.
  - Preconditions (`@requires`) are treated as input-domain filters: an input that fails
    a precondition is reported as *skipped* (out of scope), not as a bug.



### Added
- **Design-by-contract spec annotations, runtime-enforced on the EVM backend
  (provable safety, layer L2a).** Three new contract specification annotations that
  lower to revert checks in the compiled bytecode:
  - **`@requires(expr)`** — a function precondition, evaluated at entry after the
    parameters are decoded (so it can reference them). A false condition reverts with
    `precondition failed`. Multiple `@requires` on one function are AND-ed.
  - **`@ensures(expr)`** — a function postcondition, evaluated at every `return` (and on
    the implicit void return). A false condition reverts with `postcondition failed`.
    Postconditions may reference the new **`result`** keyword, which binds to the
    function's scalar return value.
  - **`@invariant(expr)`** — a contract-level invariant, declared next to `@contract`,
    re-checked after every state-mutating function and reverting with `invariant
    violated`. `@view`/`@pure` functions are exempt (they cannot mutate state).
- **`DHR-E516` — unknown identifier in a spec expression.** `ContractValidator` now walks
  every `@requires`/`@ensures`/`@invariant` expression and rejects names that do not
  resolve to a parameter, a contract field, the `result` keyword (in `@ensures`), or a
  known builtin (`msg`/`block`/`tx`). This closes a footgun: on the EVM backend an
  unresolved identifier silently compiles to `0`, which would quietly turn a spec into an
  always-true or always-false guard.

### Notes
- Additive, non-breaking: contracts without spec annotations compile to identical
  bytecode. `result` binding is supported for scalar returns; on `sab`/string returns the
  postcondition runs but `result` is unbound.

## [3.3.0] - 2026-06-18

### Added
- **Two new security detectors (provable safety, layer L1).** `SecurityAnalyzer` now
  flags **reentrancy** (`SEC-REENTRANCY`, **SWC-107**): a storage write that happens
  after an external call in the same function — the classic checks-effects-interactions
  violation — recognising both value transfers (`this.transfer(...)`) and method calls on
  an `Address`-typed storage field or parameter, and trusting `@nonreentrant` as an
  explicit guard. It also flags **`tx.origin` authorization** (`SEC-TX_ORIGIN`,
  **SWC-115**): `tx.origin` used in an equality check, which is phishable; use
  `msg.sender` instead. Both are documented in
  [`SECURITY_RULES.md`](SECURITY_RULES.md).
- **Richer SARIF for the Security tab.** Each SARIF rule now carries a `properties`
  block with `tags` (`security` plus the mapped `SWC-*` id) and a numeric
  `security-severity`, so GitHub Code Scanning buckets DhrLang alerts into
  Critical/High/Medium/Low and lets you filter by tag.

## [3.2.1] - 2026-06-17

### Fixed
- **Audit SARIF now passes GitHub's schema validation and actually ingests into Code
  Scanning.** Each result previously emitted a `fixes[]` entry that omitted the required
  `artifactChanges` property, so every SARIF upload was rejected with
  `JOB_STATUS_CONFIGURATION_ERROR` — silently, because the upload step was
  `continue-on-error`. The remediation advice is now folded into the result message and the
  rule's `help` text instead of an (invalid) fix, and the upload step no longer swallows
  validation failures. The `SarifFormatterTest` now asserts a fix is never emitted without
  `artifactChanges`.

## [3.2.0] - 2026-06-17

### Added
- **SARIF code-scanning is now first-class (provable safety, layer L0).** The security
  auditor's SARIF output (`--audit --sarif`) now emits real source line numbers
  (`region.startLine`) for every finding from the arithmetic, security, invariant, and
  validation analyzers, plus a stable per-result `partialFingerprints` value so GitHub Code
  Scanning can track and dedupe alerts across runs. Rule `helpUri`s now resolve to the new
  [`SECURITY_RULES.md`](SECURITY_RULES.md), which documents every rule family (`ARITH-*`,
  `SEC-*`, `INV-*`, `AUD-*`, `DHR-E5xx`) with severities and SWC mappings. The
  `Smart Contract Security Audit` workflow now writes a per-contract findings table to the
  run summary.

### Fixed
- **`--audit` no longer silently produces nothing on contracts that have errors.** The audit
  pipeline was gated behind a clean type-check, so any contract with a validation error (e.g.
  `DHR-E550`) yielded no report or SARIF at all — exactly the contracts most worth scanning.
  Audit is now treated as an analysis mode that runs after parsing regardless of semantic
  errors and exits `0`, surfacing those errors as findings instead of aborting.

## [3.1.0] - 2026-06-17

### Added
- **Checked / wrapping arithmetic modes** (EVM backend): per-function `@checked` and
  `@unchecked` annotations select whether `+`, `-`, `*` on `num` revert on overflow/underflow
  (`"arithmetic overflow"` / `"arithmetic underflow"`) or wrap modulo 2²⁵⁶. The compiler default
  is wrapping in this alpha and flips to checked-by-default in beta via a single constant
  (`CHECKED_ARITHMETIC_BY_DEFAULT`). Declaring both annotations on one method is rejected
  (`DHR-E515`).
- **Custom errors + `revert`** (EVM backend): declare gas-efficient typed errors with
  `@error kaam InsufficientBalance(num available, num required) {}` and raise them with
  `revert(InsufficientBalance(a, b))`. Reverts encode the Solidity-compatible 4-byte error
  selector plus ABI-encoded arguments, and the error is emitted as a `"type":"error"` entry
  in the contract ABI. `revert("message")` (Error(string)) and bare `revert()` are also
  supported, as is `require(cond, CustomError(args))`.
- **Explicit `indexed` event parameters** (EVM backend): event params are indexed only when
  declared `indexed` (e.g. `Transfer(indexed Address from, indexed Address to, num amount)`),
  driving both the ABI `indexed` flag and `LOG` topics from the same declaration. More than 3
  indexed params is rejected (EVM `LOG4` limit). Events without `indexed` now correctly default
  to zero indexed params.

### Fixed
- **EVM backend arithmetic correctness**: `-`, `/`, `%` and the comparison operators
  (`<`, `>`, `<=`, `>=`) now emit the correct **unsigned** opcodes with the correct operand
  order (`num` maps to `uint256`). Previously they used signed opcodes and/or reversed operands
  (e.g. computing `b - a`). The checked multiply now uses the standard SafeMath identity
  (`a != 0 && (a*b)/a == b`).
- **EVM peephole optimizer no longer corrupts `PUSH` data**: the optimizer walked one byte at a
  time and could misread a `PUSH`'s immediate operand as opcodes, spuriously eliminating bytes
  whenever the data matched a `PUSH+POP` / `DUP1+POP` pattern (e.g. inside embedded revert
  strings). It now copies each surviving `PUSH` together with its full immediate operand.
- **Numeric `as` casts**: `expr as num` / `expr as duo` (and `toNum` / `toDuo`) now accept
  numeric operands, not just strings. `duo as num` truncates toward zero, `num as duo` widens.
  Previously these failed at type-check, contradicting the v3.0.0 `as`-cast feature. Truncating
  a division — `(7 / 2) as num` → `3` — is now the supported way to get integer division.

## [3.0.0] - 2026-04-25

### Added — Language Features
- **Labeled break/continue**: `outer: for(...) { for(...) { break outer; } }` — jump to named outer loops
- **`as` type cast syntax**: `expr as num`, `expr as sab`, `expr as duo` — desugars to toNum/toDuo/toString
- **Labeled loops**: `label: while(...)`, `label: for(...)`, `label: do {...}` with labeled break/continue
- **Hex literals**: `0xFF`, `0xABCD` in all backends
- **String interpolation**: `"Hello ${name}!"` desugars to concatenation
- **Bitwise operators**: `&`, `|`, `^`, `~`, `<<`, `>>` across all backends + TypeChecker

### Added — EVM Blockchain Production
- **SafeMath overflow protection**: ADD reverts on overflow, SUB reverts on underflow, MUL verifies result/a == b
- **Access control codegen**: Auto-stores msg.sender as owner at deploy; private functions emit onlyOwner check
- **Reentrancy lock collision-safe**: Lock slot computed as keccak256("dhrlang.reentrancy.lock") instead of hardcoded 0xFFFF
- **EvmPeepholeOptimizer wired in**: PUSH0 substitution, constant folding, dead PUSH+POP elimination, DUP1+POP removal, SLOAD cache tracking
- **Stack depth tracking**: Every opcode adjusts tracked depth; `isStackSafe()` validates <1024 limit
- **Gas tracking**: Accumulates per-opcode gas costs; `getGasUsed()` for accurate estimates
- **Memory expansion tracking**: `getMemoryHighWater()` + `estimateMemoryGas()` for quadratic cost modeling
- **Dynamic array ABI return encoding**: Proper offset+length+data layout for array returns
- **Unsupported expressions now throw**: Instead of silently pushing 0, emitExpression throws IllegalStateException

### Added — Tooling
- **LSP server**: Full stdio-based LSP with diagnostics, completion, hover via `--lsp` CLI flag
- **VS Code extension v3.0.0**: Updated grammar (do/switch/case/default/as/emit), new keyword highlighting

### Fixed
- **For-loop continue increment bug**: `continue` in for-loops now correctly executes the increment before re-checking condition (was infinite loop)
- **TypeChecker bitwise ops**: Added BIT_AND/OR/XOR/LSHIFT/RSHIFT/BIT_NOT to type checker
- **CI/CD build job**: Fixed to skip tests in build step (already run in test step)

### Tests
- **1,287 tests, 0 failures**
- Added StorageEncoder tests (13 tests), PeepholeOptimizer tests (10 tests), CodeBuffer tracking tests (7 tests), access control tests (3 tests)

## [2.0.0] - 2026-03-05

### Added — Iteration 2: Smart Contract Safety Features
- **ViewPureChecker** (SC-201): Static analysis enforcing `view` and `pure` function modifiers; detects state reads/writes in pure functions and state writes in view functions
- **NonReentrantChecker** (SC-202): Reentrancy guard analysis with call-graph tracking, mutex validation, and cross-function reentrancy detection
- **StatementClassifier** (SC-203): Classifies statements as state-reading, state-writing, pure computation, or external calls for safety analysis
- **EffectOrderingAnalyzer** (SC-204): Checks-Effects-Interactions pattern enforcement; flags state writes after external calls
- **StorageLayouter** (SC-205): Deterministic storage slot assignment with packing optimization for types < 32 bytes; supports structs, arrays, and mappings

### Added — Iteration 3: EVM Backend
- **OpCode** (SC-301): Complete EVM opcode enum (150+ opcodes) covering arithmetic, comparison, bitwise, memory, storage, flow, logging, system, and push/dup/swap families
- **EvmAssembler** (SC-302): Assembles opcode sequences into raw bytecode with label resolution, jump patching, and PUSH optimization (PUSH1–PUSH32)
- **FunctionSelector** (SC-303): Solidity-compatible 4-byte function selector generation using Keccak-256; supports selector collision detection and function dispatch tables
- **AbiEncoder** (SC-304): ABI encoding/decoding for uint256, int256, bool, address, bytes32, string, dynamic bytes, and fixed/dynamic arrays with proper head/tail encoding
- **BytecodeOptimizer** (SC-305): Peephole optimizer with constant folding, dead code elimination, push optimization, and duplicate swap reduction; multi-pass optimization pipeline

### Added — Iteration 4: Interactive Debugging
- **BreakpointManager** (SC-401): Manages breakpoints by line, function name, or condition; supports enable/disable/toggle, hit counts, conditional expressions, and logpoints
- **DebugSession** (SC-402): Full debug session lifecycle with step-over, step-into, step-out, continue, and run-to-cursor operations; maintains variable scopes, call stack, and watch expressions
- **DebugRepl** (SC-403): Interactive debug REPL with command parsing for break, step, continue, print, watch, stack, locals, and variable evaluation
- **WatchExpression** (SC-404): Live expression evaluation during debugging with history tracking, formatting options, and error resilience
- **SourceMapGenerator** (SC-405): Generates source maps mapping bytecode offsets to source file/line/column with inline source embedding and JSON serialization

### Added — Iteration 5: Testing & Verification Framework
- **ContractTestRunner** (SC-501): Test discovery and execution engine with setup/teardown lifecycle, assertion framework, expected-exception support, and timeout enforcement
- **FuzzTester** (SC-502): Fuzz testing with random input generation for integers, strings, bytes, addresses, booleans, and arrays; configurable seed, iterations, and range constraints
- **PropertyBasedTester** (SC-503): Property-based testing with shrinking support; generates random inputs, detects failures, and automatically minimizes failing test cases
- **CoverageTracker** (SC-504): Line and branch coverage tracking with per-function reporting, HTML/JSON export, and uncovered line identification
- **MockFramework** (SC-505): Mock creation with method stubbing, call verification, argument matching (any, exact, range, predicate), and call ordering verification
- **GasProfiler** (SC-506): Gas cost estimation per opcode category (arithmetic, storage, memory, call, log) with hotspot identification and optimization suggestions
- **TestReporter** (SC-507): Multi-format test report generation (text, JSON, JUnit XML, HTML) with suite/case aggregation and timing information
- **DiagnosticsSchemaValidation**: Enhanced JSON diagnostics schema validation tests

### Added — Iteration 6: Production Deployment & Tooling
- **AuditReportGenerator** (SC-601): Security audit report generation with finding severity levels, categorized checks, executive summary, and compliance scoring
- **ContractDocGenerator** (SC-602): NatSpec-style documentation generation from annotated contracts with function signatures, parameter descriptions, and Markdown/HTML output
- **DeploymentManager** (SC-603): Multi-network deployment orchestration supporting mainnet, testnets (Goerli, Sepolia, Mumbai), and custom networks; dry-run mode, gas estimation, and deployment receipt tracking
- **L2ChainConfig** (SC-604): Layer-2 chain configuration for Optimism, Arbitrum, zkSync, Polygon, Base, Scroll, and StarkNet with gas adjustments, bridge addresses, and finality parameters
- **ExampleContractTemplates** (SC-605): Production-ready contract templates for ERC-20, ERC-721, multi-sig wallet, and governance contracts with configurable parameters

### Added — Iteration 7: AI Agent & Data Pipeline Framework
- **AgentAnnotations** (SC-701): Annotation system for AI agent definitions (`@agent`, `@tool`, `@model`, `@prompt`, `@guardrail`, `@memory`, `@retry`, `@timeout`, `@stream`) with validation, target checking, and conflict detection
- **AgentRuntime** (SC-702): AI agent execution environment with tool registry, conversation memory, execution context, token tracking, multi-model support, and streaming callbacks
- **AgentPlanner** (SC-703): Multi-step task planning with dependency resolution, topological execution ordering, parallel step detection, plan optimization, and execution with retry/timeout support
- **PipelineConfig** (SC-704): Data pipeline DSL with typed stages (MAP, FILTER, REDUCE, FLATMAP, SORT, DISTINCT, LIMIT, SKIP, GROUP_BY, JOIN, WINDOW, AGGREGATE), validation, and optimization
- **PipelineExecutor** (SC-705): Pipeline execution engine supporting all stage types with statistical aggregations (SUM, AVG, MIN, MAX, COUNT, MEDIAN, VARIANCE, STDDEV, PERCENTILE), windowing, and error handling
- **AgentPipelineIntegration** (SC-706): Bridges AI agents with data pipelines; intelligent pipeline generation from natural language, anomaly detection, auto-optimization, and pipeline explanation

### Testing
- **1,034 total tests, 0 failures** (up from 146 in v1.1.3)
- Iteration 2: ~100+ tests for smart contract safety analysis
- Iteration 3: ~100+ tests for EVM backend correctness
- Iteration 4: ~100+ tests for interactive debugging
- Iteration 5: ~120+ tests for testing/verification framework
- Iteration 6: ~100+ tests for deployment and tooling
- Iteration 7: ~217 tests for AI agent and data pipeline framework
## [2.0.0] - 2026-01-28

### Major Release — 7 Iterations of New Features (1,034 tests, 0 failures)

### Added — Iteration 1: Enhanced Error Reporting
- Unique error codes with DHR-EXXX/DHR-WXXX format for easy searchability
- All errors now include line number, column, and error code
- Contextual hints for every error type with actionable suggestions
- Type-aware hints (e.g., suggesting 'sab' when 'string' is used)
- Multi-Dimensional Array test suite (20+ cases covering 2D–4D arrays)
- FUTURE_ENHANCEMENTS.md with Agile sprint plans

### Added — Iteration 2: Smart Contract Safety Features
- **ViewPureChecker** — enforces `view`/`pure` function semantics and state-access rules
- **NonReentrantChecker** — static reentrancy guard analysis with call-graph traversal
- **StatementClassifier** — classifies statements as reads, writes, calls, or transfers
- **EffectOrderingAnalyzer** — checks-effects-interactions pattern enforcement
- **StorageLayouter** — EVM-compatible storage slot assignment with packing and alignment

### Added — Iteration 3: EVM Backend
- **OpCode** — complete EVM opcode enum with gas costs, stack effects, and categories
- **EvmAssembler** — EVM bytecode assembly with label resolution and jump patching
- **FunctionSelector** — Solidity-compatible 4-byte function selector generation (Keccak-256)
- **AbiEncoder** — ABI encoding/decoding for uint256, address, bool, string, bytes, arrays, tuples
- **BytecodeOptimizer** — peephole optimizations, dead code elimination, constant folding, jump threading

### Added — Iteration 4: Interactive Debugging
- **BreakpointManager** — file/line/conditional/hit-count breakpoints with enable/disable
- **DebugSession** — full debug session lifecycle with step-over, step-into, step-out, continue
- **DebugRepl** — interactive debug REPL with expression evaluation and variable inspection
- **WatchExpression** — watch expressions with change detection and conditional watches
- **SourceMapGenerator** — bidirectional source map generation (source ↔ bytecode offset)

### Added — Iteration 5: Testing & Verification Framework
- **ContractTestRunner** — smart contract test discovery, execution, and lifecycle management
- **FuzzTester** — coverage-guided fuzzing with boundary, mutation, and dictionary strategies
- **PropertyBasedTester** — property-based testing with shrinking and reproducible seeds
- **CoverageTracker** — line, branch, function, and contract-level coverage tracking
- **MockFramework** — mock contract creation with call recording and return value stubbing
- **GasProfiler** — per-function and per-opcode gas profiling with hotspot detection
- **TestReporter** — multi-format test reporting (text, JSON, JUnit XML, HTML, Markdown)
- **DiagnosticsSchemaValidation** — JSON schema validation for diagnostic output

### Added — Iteration 6: Production & Deployment Tooling
- **AuditReportGenerator** — security audit reports with severity scoring and SARIF export
- **ContractDocGenerator** — NatSpec-compatible documentation generation (HTML, Markdown, JSON)
- **DeploymentManager** — multi-chain deployment with verification, proxy patterns, and gas estimation
- **L2ChainConfig** — Layer-2 chain configuration (Optimism, Arbitrum, zkSync, Polygon, Base, etc.)
- **ExampleContractTemplates** — production-ready templates (ERC-20, ERC-721, Governor, Vault, etc.)

### Added — Iteration 7: AI Agent & Data Pipeline Framework
- **AgentAnnotations** — `@agent`, `@tool`, `@model`, `@prompt`, `@guardrail`, `@memory`, `@retry`, `@pipeline`, `@transform`, `@schema` annotations with full validation
- **AgentRuntime** — AI agent execution engine with tool dispatch, memory management, and guardrails
- **AgentPlanner** — multi-step planning with ReAct, chain-of-thought, and tree-of-thought strategies
- **PipelineConfig** — data pipeline configuration with stages, connections, and validation
- **PipelineExecutor** — streaming/batch pipeline execution with backpressure, windowing, and fault tolerance
- **AgentPipelineIntegration** — intelligent pipelines combining AI agents with data processing

### Changed
- ErrorCode enum restructured with unique code strings (DHR-E201, etc.)
- ErrorMessages enhanced with DhrLang-specific type hints
- Version bumped from 1.2.0 to 2.0.0 to reflect major feature additions

### Testing
- **1,034 tests total, 0 failures** across all 7 iterations
- Full test coverage for all new subsystems
- Integration tests verifying cross-module interactions

## [1.1.3] - 2025-11-23

### Added
- **Production-ready release configuration**
  - Shadow JAR (fat JAR) properly configured with `Main-Class` and `Implementation-Version` manifest attributes
  - Comprehensive CLI with all options documented: `--help`, `--version`, `--json`, `--time`, `--no-color`, `--backend`, `--emit-ir`, `--emit-bc`
  - JSON diagnostics with stable schema contract (v1) validated by automated tests
  - `RELEASE_CHECKLIST.md` with complete release workflow and verification steps

### Changed
- **Gradle build improvements**
  - Fixed all script-level deprecation warnings for Gradle 9/10 compatibility
  - Updated property assignment syntax (`group = 'value'` instead of `group 'value'`)
  - Adjusted Jacoco coverage thresholds to realistic baseline (40% instruction, 28% branch)
  - Signing configuration updated with modern syntax

- **Documentation updates**
  - README.md: Added comprehensive CLI options section, JSON diagnostics documentation, and production JAR usage
  - GETTING_STARTED.md: Updated with v1.1.3 JAR paths, added exception handling examples with typed catches
  - All docs now reference current version (1.1.3) and fat JAR artifact

- **Exception handling stabilized**
  - Typed catch support fully implemented across AST, IR, and bytecode backends
  - Exception type matching for `any`, `Error`, `DhrException`, and custom exception types
  - Complete parity tests between AST/IR/bytecode for exception semantics

- **IR and Bytecode backends**
  - Full exception support with `IrThrow`, `IrTryPush`, `IrTryPop`, `IrCatchBind` instructions
  - Bytecode VM with typed exception handlers and proper call stack management
  - Fixed `Block` lowering for parser-desugared for-loops
  - Introduced `NO_EXCEPTION` sentinel to safely handle null exception states

### Fixed
- CLI tests (`CliSmokeTest`, `DiagnosticsSchemaValidationTest`) now robust to missing JAR files, falling back to classpath execution
- JSON mode outputs clean JSON only (no banners or extra lines mixed in)
- NullPointerException in bytecode VM when pushing null exceptions to ArrayDeque
- IR parity tests showing duplicate output due to missing Block lowering

### Testing
- All 146 tests passing (2 expected skips in exception propagation tests)
- Complete parity test coverage for IR and bytecode backends (arrays, calls, fields, exceptions)
- CLI smoke tests for help, version, and JSON mode
- Diagnostics JSON schema validation test ensuring contract stability

## [1.1.2] - 2025-09-30

### Added
- CLI flags: `--help`, `--version`, improved `--json` path (structured usage output).
- CLI smoke tests (`CliSmokeTest`) exercising flags & JSON diagnostics.
- Diagnostics JSON schema file (`diagnostics.schema.json`).
- `--time` phase timing support (lex/parse/type/exec) with merged JSON object (`schemaVersion`=1).
- `--no-color` flag to disable ANSI sequences for CI/plain log environments.

### Changed
- Updated CHANGELOG and README to reflect implemented timing & color suppression (removed planned placeholder items).


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

