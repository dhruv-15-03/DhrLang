# Stage 7 qualification runbook

Status: executable offline preparation, not collected evidence or release approval.
Read the [qualification gates](learner-preview-gates.md) and
[candidate support contract](release-support-contract.md) first.

## 1. Freeze the protocol before recruiting

The maintainer must approve the candidate component/platform scope, support
owner, baseline language, comparable tasks and help rules. Identify the exact
compiler/VSIX SHA-256 and source revision used for each session. Do not publish
new versions merely to conduct a local pilot. Use immutable candidate artifacts;
if fixes change them, retain which participant used which artifact.

An instructor prepares the capstone and held-out transfer variant before the
pilot. Do not reuse the public reference solution as a held-out assessment.
Record prior programming experience for both groups and explain assignment,
task/help comparability and any deviations. The baseline has no invented pass
threshold here; its purpose is a documented comparison, not an automatic claim
that DhrLang teaches better.

## 2. Recruit and obtain consent

Recruit at least 20 independent DhrLang learners and a comparison group.
Agent sessions, maintainer demonstrations and test fixtures are not learners.
Ask participants to consent to local collection of setup/task outcomes for this
evaluation. Explain the data collected, who can access it, retention period,
withdrawal process and whether anonymized aggregate findings will be published.
These details and recruitment are human responsibilities, not consent inferred
by a script. No payment, email campaign or external account is created here.

Allocate identifiers such as P-0001. Keep identity mappings and consent records
in an access-controlled location outside Git. Do not collect passwords, SAP
credentials, customer transactions or raw personal details in the outcome file.
Define how withdrawals affect retention and report exclusions transparently;
never erase failures merely to improve a denominator.

## 3. Run and record the sessions

Record every enrolled consented participant, including setup failures and
non-returners. Separately retain OS/Java/artifact, experience, help and friction
notes in the restricted collection log. Measure integer elapsed seconds to the
first correct run; use null for missing/failed first-run outcomes, not zero.
The 600-second setup threshold applies to supported setups.

Record capstone completion and administer the instructor-held-out variant
without AI to completers. Unattempted or unavailable transfer results remain
in the completer denominator. AI-assisted passes cannot count as AI-off passes.
Do not coach participants without recording the help.

The following is a **schema illustration only**, not an actual participant:

```json
{
  "schemaVersion": 1,
  "participants": [
    {
      "id": "P-0001",
      "group": "dhrlang",
      "consented": true,
      "supportedSetup": true,
      "firstCorrectRunSeconds": null,
      "capstoneCompleted": null,
      "transferAttempted": false,
      "transferOutcome": "missing",
      "aiOff": false
    }
  ]
}
```

Group is `dhrlang` or `baseline`; IDs must be unique across both. Capstone is
true, false or null. Transfer outcome is `passed`, `failed` or `missing`.
`aiOff` is an observed condition of an attempted transfer, not a default claim.
Consent, supported setup, attempted and AI-off fields must be JSON booleans.
Do not feed non-consented records to the tool.

## 4. Calculate and inspect the evidence

With Python 3.12, run the offline tool against explicitly selected local files:

```powershell
py -3.12 tools\learning\qualification.py --records C:\pilot\consented-outcomes.json --output C:\pilot\new-aggregate.json
```

It reads at most 2 MiB, rejects unknown/duplicate fields, duplicate participants,
inconsistent records and fractional/boolean time values. It does not execute
source, contact a service or overwrite an existing aggregate. Output contains
counts and exact threshold comparisons, not participant IDs or raw outcomes.
Exit 0 means an aggregate was written, **not that the thresholds passed**;
inspect `numericalLearnerThresholdsMet` and each numerator/denominator.
Exit 2 means invalid input or file failure.

The tool counts setup and capstone against all enrolled DhrLang records and
transfer against all capstone completers. Nulls, failures, unsupported setups
and unattempted transfer do not shrink those denominators. It reports the
baseline separately. For 20 enrolled/12 completers, the required counts are
16 timely supported first runs, 12 capstones and 9 AI-off transfer passes.
Comparisons use integer arithmetic, not rounded displayed percentages.

Self-reported arithmetic does not authenticate consent, independence, timings,
baseline comparability or mastery. Those verification flags and release
authorization remain false even when every numerical threshold is met.
Do not commit raw outcome files or private logs. Review aggregate privacy before
sharing; small groups can be identifying even without names.

## 5. Obtain independent review receipts

Two instructors/practitioners review purchase-approval policy, currency/minor-unit
limits, authority, tenant separation, shared fixtures and failure/replay outcomes.
Retain each reviewer's expertise, independence, artifact revision, findings,
severity, disposition and unresolved limitations in consented review receipts.

An independent isolation reviewer examines the actual local execution boundary,
budget enforcement, credentials, file/process/network access, failure/replay
behavior and support/rollback plan. A bounded subprocess is not an OS sandbox.
Do not imply review covers an untested production host or live SAP deployment.
An automated scan is useful technical evidence, not an independent human receipt.

## 6. Close findings before promotion

Resolve security and review findings or document explicit applicable limitations
for maintainer acceptance; do not suppress alerts or silently narrow scope.
If user-facing fixes alter the measured behavior, retain the earlier failures
and obtain appropriate repeat evidence instead of transferring old pilot scores
to a different artifact without explanation.

Combine the actual pilot/baseline evidence, domain/isolation receipts, approved
support/migration scope and exact candidate test/hash manifest for review.
Do not set a global "qualified" flag from the arithmetic alone. Follow the
[release checklist](../RELEASE_CHECKLIST.md) only after exact version/commit and
publication approval. No tag, Marketplace publication or public hosting is
authorized by this runbook.
