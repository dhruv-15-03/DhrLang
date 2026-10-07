# Offline learning tools (experimental)

**Availability:** `doctor` and `learn` are unreleased commands in current `main`
source builds. They are **not** included in the published v4.0.2 binaries, even
though the current development build still reports version `4.0.2`.

## First session

Requires Java 17+ and a standalone compiler built from the current source.
No AI provider, account, network connection or SAP system is needed after the
compiler is built.

From the repository root in PowerShell:

```powershell
.\gradlew.bat stageCompiler
java -jar build\compiler\DhrLang.jar doctor
java -jar build\compiler\DhrLang.jar learn list
java -jar build\compiler\DhrLang.jar learn show 01-input
java -jar build\compiler\DhrLang.jar learn start 01-input answer.dhr
java -jar build\compiler\DhrLang.jar learn check 01-input answer.dhr
java -jar build\compiler\DhrLang.jar learn hint 01-input 1
```

On macOS/Linux, use `./gradlew stageCompiler` and forward slashes in paths.
The starter intentionally fails at least one practice case. Predict its result,
edit `answer.dhr`, and run `learn check` again. Hints default to level 1; ask for
level 2 or 3 explicitly.

`doctor` inspects the current Java version, JAR manifest, required packaged
class/catalog entries and temporary-file access. Java must already be new enough
to launch the compiler. These checks are not a cryptographic artifact check,
complete runtime test, correctness certificate or OS-sandbox assessment.

`learn start` creates a file only when the destination does not exist. It never
overwrites learner work. Grading reads the submission without editing it and
runs cases sequentially in fresh bounded JVMs. Source is not uploaded or sent
to a model.

## Ten exercises

| Exercise | Skill | Transfer target |
|---|---|---|
| 01-input | Explicit input and arithmetic | Java/TypeScript input conversion |
| 02-bounds | Conjunction of business constraints | Integer minor units and validation |
| 03-boundary | Inclusive boundary tests | Mainstream unit testing |
| 04-arrays | Safe indexing | Runtime error versus explicit rejection |
| 05-loop | Loop bounds and accumulation | Trace and state invariants |
| 06-functions | Reusable pure calculations | Independent unit tests |
| 07-strings | Explicit normalization | Input validation pipelines |
| 08-errors | Conversion failure boundaries | Exception handling |
| 09-state | Object-state updates | Java object/local/static state |
| 10-approval | Composition of domain rules | Trusted host identity and authorization |

Every exercise includes an incorrect starter, three transparent practice cases,
three progressive hints and a mainstream-language transfer task. `learn show`
prints the input/output cases as well as the task and starter.

These small examples retain existing JVM integer semantics; they do not provide
production money handling or comprehensive boundary coverage. The approval
exercise uses synthetic identities. A real host must authenticate users and
enforce authorization; requester/approver text is not a credential.

## Grading and local progress

A case passes only when the worker reports `SUCCESS`, stderr is empty and stdout
matches the expected value. Comparison normalizes CRLF to LF and removes one
final newline; it does **not** trim spaces or ignore extra lines. Correct-looking
output followed by a failure does not pass. Failure output includes the case's
input, expected/actual output, execution status and available diagnostic positions.

For machine-readable evidence and optional local records:

```powershell
java -jar build\compiler\DhrLang.jar learn check 01-input answer.dhr --json --record attempt-01.json
java -jar build\compiler\DhrLang.jar learn progress attempt-01.json
```

`--record` writes a new JSON file and refuses to overwrite any existing file.
It records failed attempts too. Without this option no progress file is created;
there is no hidden home-directory database or telemetry. `progress` accepts
one or more explicit report paths and displays their recorded counts and source
hashes. It does not rerun submissions or assert that their current contents pass.
Invalid or inconsistent report data is rejected.

Reports include a schema version, executed JAR version, profile, source SHA-256,
case inputs/expected/actual output, bounded stderr and diagnostics. They omit the
full submitted source, but program output and diagnostics can contain its data;
review a report before sharing it. Retain the compiler artifact hash separately:
a version string alone does not identify the exact development build.

Records are editable local evidence, not signed certificates or anti-cheating
results. Passing the visible cases does not establish general correctness or
independent mastery. Predict a new case, explain the fix, and complete the
transfer task without an AI assistant.

## Optional redacted tutor handoff (offline preparation)

The standard-library Python 3.12 tool prepares evidence for a user-selected
client; it does **not** implement an AI provider, dispatch tools, execute the
submission or send anything over the network. Python is needed only for this
optional exporter, not the compiler or ordinary learning commands.

After saving an attempt, from the repository root in PowerShell:

```powershell
$compilerHash = (Get-FileHash build\compiler\DhrLang.jar -Algorithm SHA256).Hash.ToLowerInvariant()
py -3.12 tools\learning\evidence.py --report attempt-01.json --submission answer.dhr --compiler-sha256 $compilerHash --level 1 --output tutor-01.json
py -3.12 -m unittest discover -s tools\learning -p 'test_*.py' -v
```

On macOS/Linux use `python3` and the platform's path separators. The exporter
checks the saved report's exact schema, catalog case identities/inputs/expected
outputs, status/pass/count consistency and submission SHA-256. It refuses to
overwrite its output; invalid evidence or file failures return exit 2. Report
reads are limited to 2 MiB and 64 nesting levels; submission limits match the
learner commands. No progress/pass record is modified.

The new JSON contains failed-case references, statuses, output-match flags,
stderr-presence flags and diagnostic positions. It omits source, raw inputs,
outputs, diagnostic messages/codes and report messages. Compiler version/hash,
source hash, exercise identity and result counts remain visible; treat them as
potentially sensitive metadata and inspect the bundle before sharing it.
The recorded compiler hash is supplied metadata, not authenticated evidence of
which artifact produced an editable historical report.

Levels 1-3 select existing lesson hints only for successful executions with an
output mismatch and no stderr. Compilation/runtime/infrastructure failures do
not receive an unrelated lesson hint. A hint does not establish the root cause,
and an all-pass report does not prove mastery. No trace is fabricated.

Client-policy fields recommend one model call, 512 output tokens, ten seconds
and no tools/external writes. These are **not enforced provider budgets**:
there is no provider in this exporter. A future client must enforce limits,
consent, tool allowlists and cancellation itself, and recheck any proposed edit
through `learn check`. The exporter tests (including synthetic malformed
reports) are not the required 50 independent held-out learner programs.

## Bounded source-linked traces

Trace one numbered practice case:

```powershell
java -jar build\compiler\DhrLang.jar learn trace 05-loop answer.dhr 2
java -jar build\compiler\DhrLang.jar learn trace 05-loop answer.dhr 2 --json
```

The trace records at most the first 128 bytecode instructions actually reached,
with function/index, instruction index, call depth and source filename/line/column.
It observes instructions **before execution**: reaching a `THROW`, array access or
division does not prove that operation succeeded. Always read the execution status.
Locations refer to the submission identified by its SHA-256; the worker names
the source `request.dhr`. A zero location denotes compiler-generated code without
source provenance. Desugared control flow can produce multiple steps on one line.

Trace mode disables IR optimization to retain instruction-to-source provenance;
the bytecode format and language semantics are unchanged. Its instruction count
and timing can differ from normal optimized grading. It is a separate execution,
not a replay of an earlier check. A successful trace is **not** an exercise pass:
use `learn check` for all practice cases.

Local values, heap contents, source text and timestamps are not recorded in trace
steps. Function names/positions are visible, and ordinary stdout/stderr remains
part of the result. Once the trace prefix fills, execution continues under its
original budgets and `truncated` is true. Runtime errors retain the prefix. A
worker killed by wall-clock/output limits may have no trace because its result
was not written; the failure status remains authoritative.

## Limits and exit codes

Each case uses the experimental `jvm-bytecode-v1` host profile with a five-second
wall-clock budget, one million VM steps, a 128 MiB Java heap cap and 4096 combined
stdout/stderr bytes. Budgets include worker startup and compilation. Submissions
must be UTF-8, no larger than 512 KiB and 131072 source characters. Saved reports
are limited to 2 MiB. Cases do not execute concurrently in the grader.

| Exit | Meaning |
|---|---|
| 0 | Command succeeded; for `check`, all cases passed |
| 1 | `check` found a failed case, or `doctor` found an incomplete installation |
| 2 | File/packaging/worker/interruption failure, or a failed `trace` execution |
| 64 | Invalid command, exercise, hint/case number or submission-size argument |

Usage/setup errors go to stderr rather than fabricated successful JSON. A
worker infrastructure failure is distinct from an ordinary failed practice case.

The host is process-separated, **not an OS sandbox**. Do not expose this grader
to hostile multi-tenant workloads without external isolation. No imports,
numeric-default changes, SAP calls or autonomous AI writes are introduced.

## Human qualification still required

The next offline capstone is the [purchase-approval lab](ENTERPRISE_LAB.md).
It adds a shared DhrLang/Java fixture corpus and a simulated host-owned workflow;
it is not a live SAP integration or a completed human pilot.

Automated compiler/lesson tests are not learner-pilot results. The agreed gates
remain pending:

- Recruit at least 20 independent learners and record prior experience, help,
  setup friction, completion and dropouts. At least 80% should reach their first
  correct run within 10 minutes on supported setups.
- At least 60% of enrolled learners should complete the capstone; at least 70%
  of completers should solve a held-out transfer variant without AI. Report both
  denominators and compare with an existing-language baseline. The fuller
  enterprise capstone is a separate Stage 5 deliverable, not this starter lesson.
- Obtain instructor feedback; two instructors or practitioners must review the
  enterprise capstone's business semantics before that qualification claim.
- Obtain independent runtime/isolation review before any production multi-tenant
  claim. Passing local tests, a doctor check or a pilot is not that review.

No participation, learning advantage or production certification is asserted here.

The [learner-preview Stage 6-7 readiness protocol](design/learner-preview-gates.md)
defines the remaining grounded-assistance evaluation and human qualification
evidence. Its stage numbering is separate from the older bytecode/blockchain
roadmap phases; it does not claim that an AI client or qualification is shipped.
