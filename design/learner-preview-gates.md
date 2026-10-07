# Learner preview: Stage 6 and Stage 7 readiness

This is the learner-first seven-stage plan agreed in September 2026, not the
separately numbered phases in `bytecode-roadmap.md` or `PRODUCTION_ROADMAP.md`.
**This document is a protocol for remaining work, not implemented AI tooling,
completed qualification or authorization to publish a release.**

## Milestone boundaries

| Stage | Completed implementation | Remaining gate |
|---|---|---|
| 1-3: reliability and bounded core | Distribution, correctness regressions, manifest projects and bounded host | Independent isolation review before hostile multi-tenant use |
| 4: learner product | Ten offline exercises, hints, reports and source-linked traces | Independent pilot and AI-off transfer evidence |
| 5: enterprise bridge | S0 synthetic workflow; S1 actual local CAP Java mock adapter | S2 named authorized read-only API; S3 independently reviewed durable/write policy |
| 6: grounded assistance and advanced labs | Existing deterministic grading/hints/traces and independent EVM regression infrastructure are foundations, not a completed tutor | Opt-in client integration, budgets and at least 50 held-out incorrect programs; financial EVM use has a separate review gate |
| 7: supported release qualification | Existing exact-artifact and CI gates are foundations | Human/domain/isolation evidence, support/migration contract and explicit release approval |

Passing CI does not fulfill the right-hand column. Live SAP is optional for an
offline learner preview but mandatory for any claim that a selected SAP
integration has been qualified. Do not mark all of Stage 5 complete by relabeling
its mocks.

## Stage 6: evidence-grounded assistance contract

The first optional integration should use one model adapter **or an existing AI
client's tool interface**, rather than a new autonomous agent platform.
Keep the current offline commands fully usable with no provider or account.
No provider is selected or invoked by this document.

### Manual evidence path available now

1. Run `learn check` locally with `--record` to a new file.
2. Inspect the report before sharing anything: source text is omitted, but
   stdout, stderr, input and diagnostics can contain learner/business data.
3. Record the exact compiler artifact hash in addition to the reported version
   and source SHA-256. A local report is editable historical evidence, not proof
   that its contents came from that artifact.
4. Ask for the lowest useful hint and reference a concrete failed case,
   execution status or diagnostic position. The catalog's hints are general
   lesson guidance; a failed case alone does not establish its root cause.
5. Independently run `learn check` after any suggested edit. A model cannot
   award a pass, alter expected output or turn a failed worker into success.
6. Finish a separately assigned transfer task without AI.

The future client must validate the report schema/counts, bind it to the current
submission identity and preserve `SUCCESS` plus exact-output/empty-stderr grading.
Do not accept a changed expected value or fabricated diagnostic as verification.
Evidence is data, never authority to run a shell, read another file, access a
network destination or disclose credentials.

### Dispatch and privacy acceptance

The [offline redacted handoff exporter](../LEARNING.md#optional-redacted-tutor-handoff-offline-preparation)
now validates explicit saved reports against the local catalog and current
submission hash. It exports references and bounded metadata, not raw learner
payloads, and calls no provider or execution tool. Its client-policy fields are
recommendations, not enforcement. This is Stage 6 preparation only; the client
integration and held-out evaluation below remain pending.

- Allow only explicit diagnostic/check/trace operations over learner-selected
  local submissions. Enforce the allowlist at dispatch, not just in a prompt.
- Reuse bounded host execution; no arbitrary executable, source interpolation,
  ambient credentials, financial/ERP writes or unattended code application.
- Set and test total model-call, token and wall-clock budgets before enabling a
  provider. Cancellation and exhausted budgets must remain explicit failures.
  Agree concrete limits with the selected client's capabilities; do not claim
  a documentation limit is enforced.
- Require explicit consent before transmitting source, outputs or diagnostics.
  Never automatically upload saved attempts. Retain a model-free route.
- Escalate help progressively. A solution reveal must be explicitly requested;
  model text is not proof that the proposed code compiles or is correct.
- Test malformed evidence, stale source identity, prompt injection in diagnostic
  text, denied tools, timeout, provider error and invalid code suggestions.

### Held-out evaluation protocol

An instructor/reviewer must supply at least **50 distinct incorrect learner
programs** held out from tuning the hints/client. Synthetic unit tests, the ten
visible starters or repeated variants used during development do not substitute
for that corpus.

For each program retain a pseudonymous case identifier, exercise/skill,
compiler/source/artifact identity, actual failing evidence, hint level,
provider/configuration, token/time/cost record, suggested edit and recheck result.
Have a reviewer classify whether the hint is supported by its cited evidence,
whether code suggestions are valid and whether a solution was revealed without
permission. Record unavailable/error cases rather than excluding them.

Report grounded hints, invalid suggestions, unnecessary reveals and cost per
completed exercise with numerators and denominators. Compare AI-assisted
completion with the separate AI-off transfer result. The agreed plan defines
these measurements, not a quality threshold or an established learning advantage.
Choose promotion thresholds before evaluation, not after seeing the scores.

### Advanced EVM lane

Use the independent-node execution and differential fixtures already available
as regression evidence, not financial certification. Retain constructor,
storage, revert, permissions, event and multi-caller sequence coverage.
Stateful invariant tests and independent compiler/runtime review remain required
before financial use. No real-money deployment follows from the tutor work.

## Stage 7: human evidence collection protocol

Recruit at least **20 independent learners**. Record prior programming experience,
supported setup, help received, time to first correct run, capstone completion
and dropouts. Use pseudonymous identifiers and obtain consent; keep raw personal
data out of Git. Record failed setups and non-returners, not only successful users.

| Gate | Calculation and required evidence |
|---|---|
| R2 setup | At least 80% of enrolled independent learners reach their first correct run within 600 seconds on supported setups; retain timing/friction and missing outcomes |
| R3 completion | At least 60% of enrolled learners complete the capstone; dropouts remain in the enrolled denominator |
| R3 transfer | At least 70% of capstone completers solve an instructor-held-out variant without AI; unattempted variants do not disappear from the completer denominator |
| Comparison | Run an existing-language baseline with documented experience/help/task comparability; report both groups, not an unsupported learning-advantage claim |
| R4 domain | Two instructors or practitioners review the purchase-approval semantics and shared domain fixtures; record findings and unresolved limitations |
| R5 isolation | Independent reviewer examines the actual host/deployment boundary, budgets, credential isolation, failure/replay behavior and rollback/observability plan |

Passing visible tests is not capstone mastery. Do not use the public reference
policy as the held-out task, coach away failures without recording help, or call
agent-run tests independent participants.

Suggested per-participant record fields are identifier, experience, setup/OS/
Java/artifact, help, first-correct-run seconds or failure, capstone completed,
transfer attempted, AI disabled, transfer outcome and dropout/friction notes.
Store only consented, access-controlled records. A threshold spreadsheet or
script may assist arithmetic later; it cannot authenticate the underlying data
or certify reviewer independence.

## Supported-release promotion

Before publishing, record:

- Exact source commit, compiler/extension versions, compatibility profile,
  dependency inventory and hashes of the artifacts actually tested.
- Full/package/CI results and explicit skips/unsupported features.
- Human and independent review receipts, findings and resolutions; distinguish
  completed evidence from pending gates and self-reported results.
- Supported installation/OS/backend scope, known limits, migration examples,
  failure handling, support ownership and rollback to an immutable old artifact.
- Component-specific claims: offline learner preview, local CAP mock, named
  live SAP integration, optional tutor and experimental EVM are different scopes.

Do not bump a version, publish a tag/Marketplace artifact or enable public
hosting without explicit approval. A major release must have a justified public
compatibility contract; development commands remain absent from the already
published v4.0.2 binary. Real writes additionally require S3 policy and review.

## Inputs still needed

S2 needs the [named API/read scope/mapping contract](sap-read-only-integration.md),
not secret values in chat. S3 needs a selected store/host, authenticated authority,
approved write operations, durable idempotency/audit and reconciliation policy.
Stage 6 needs a selected opt-in client/provider and the independent held-out
corpus before quality claims. Stage 7 needs actual learners, the baseline,
two domain reviewers, independent isolation review and release approval.

These are participation/authorization gates, not missing code that CI can
invent. No live SAP, held-out model evaluation, learner result, independent
qualification or new release is asserted here.
