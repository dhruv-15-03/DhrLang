# Purchase-approval lab (experimental, offline mock)

**Availability:** this is an unreleased current-`main` source-build feature, not
part of the published v4.0.2 binary. It is Stage 5's **S0** increment: a DhrLang
decision program, a Java reference policy and a plain Java in-memory teaching
adapter. It is **not CAP Java, a SAP connection, a real transaction service or
production qualification**.

## Run the lab

After building the standalone compiler from current source:

```powershell
.\gradlew.bat stageCompiler
java -jar build\compiler\DhrLang.jar learn enterprise cases
java -jar build\compiler\DhrLang.jar learn enterprise verify
java -jar build\compiler\DhrLang.jar learn enterprise start PurchasePolicy.dhr
java -jar build\compiler\DhrLang.jar learn enterprise verify PurchasePolicy.dhr --json
java -jar build\compiler\DhrLang.jar learn enterprise demo --json
```

On macOS/Linux use `./gradlew` and forward slashes in paths. No account, AI
provider, network connection or SAP installation is needed once the compiler is
built. `start` exports a **working reference**, not another deliberately broken
starter, and refuses to overwrite existing files.

The verification command runs the same 15 transparent fixtures against both the
DhrLang program and a Java reference, and compares both with independently listed
expected results. The cases cover approval, an exact budget, overspending, zero
and negative amounts, supplier validity, request/budget/approval-limit currency
mismatches, self-approval, missing role, cost-center scope, escalation, a closed
state and adjacent values at the numeric profile boundary.

The Java reference lives in `PurchaseApproval.java`; the packaged DhrLang source
and fixtures are in `src/main/resources/dhrlang/enterprise/`. Verification does
not execute a real business update. A pass is fixture agreement, not proof of
general correctness, authority, skill or SAP compatibility.

## Money is a bounded integer, with an explicit currency

Amounts are integer minor units: the INR fixtures use paise. Request currency,
budget currency and the approver's limit currency must all match. There is no
implicit FX conversion, rounding, tax handling or currency-exponent lookup.
Currency tags are checked syntactically, not certified against an ISO registry.

The lab's technical maximum is **9007199254740991 minor units**, the largest
integer below 2^53. Budgets, limits and remaining balances must be nonnegative
and within that bound. A request may contain a bounded negative or zero amount
so the policy can demonstrate rejection, but it cannot approve it.

This is a **precision/profile limit, not a business spending cap**. Current JVM
relational comparisons convert numbers to doubles; the entire signed-64-bit
comparison range is therefore not an exact-integer financial contract. This
increment does not change VM numeric semantics. Input data outside the supported
range is rejected, never rounded or clamped.

The host parses integer JSON tokens without float/scalar coercion and parses
policy output with `Long.parseLong`. Values such as `12500.5`, `12500.0` and the
JSON string `"12500"` are not accepted as integer input tokens. Host subtraction,
reservation deltas and recomposed totals use exact integer arithmetic and range
checks. The returned remaining balance must agree with the Java policy.

The exported DhrLang program also rejects out-of-profile numeric input. Its
stdin protocol has twelve lines: amount, available budget, approval limit,
request currency, budget currency, limit currency, requester, approver,
approval-role flag, cost-center-scope flag, supplier-active flag and workflow
state. The adapter generates these from validated data, not source interpolation.
The program prints three lines: outcome, reason and remaining minor units.

## The host owns authority and state

The program can **propose** `APPROVED`, `REJECTED` or `NEEDS_REVIEW`. It never
receives a store, credential, transaction or network capability.

`PurchaseWorkflow` checks a simulated host principal's tenant, role, cost-center
scope and separation from the requester before invoking the program. It compares
the proposal with the Java policy, then rechecks access, revisions and the policy
while applying the in-memory update. An altered program cannot authorize a
forbidden caller, change a currency or allocate a different amount.

The principal object in this lab is test data, **not authentication**. A future
real adapter must derive actor and tenant from a trusted authenticated context;
it must never accept the fixture's `mayApprove` or tenant fields as credentials.

| Result | Effect in the mock |
|---|---|
| Approved within authority | Move to `APPROVED`, reserve exactly the requested minor units, increment request/budget revisions |
| Above the actor's limit | Move to `NEEDS_REVIEW`; no budget reservation |
| Invalid amount/supplier/currency or insufficient budget | Record rejection evidence; no request-state or budget transition |
| Wrong role/scope/tenant or self-approval | Deny before execution; no receipt or state change |
| Closed or stale request/budget revision | Conflict; do not silently overwrite |
| Invalid/failed DhrLang result or reference disagreement | Explicit failure; no commit |

An authorized second approver with a sufficient same-currency limit can approve
a `NEEDS_REVIEW` request using its current revisions and a new event ID.
There is no separate manual-rejection, request-editing, refund, accounting or
inventory subsystem here.

## Idempotency and uncertain outcomes

An event key is bound to the command's request/revision pair, normalized business
payload, authorized actor and tenant. Payload identity includes request/supplier/
cost-center/currency/amount facts plus budget currency and the actor's
currency-tagged approval limit. IDs/tags are validated and numeric values are
exact integers. Changing that intent produces a conflict, not reuse of a cached
approval.

Replay and reconciliation paths check the caller's current tenant, role and
scope before returning a receipt. A normal replay keeps the **original**
expected revisions; current state/balance changes caused by the original commit
are not mistaken for a new payload. New approval terms or a changed command
require reconciliation rather than blindly reusing the old key.

`demo` explicitly exercises:

1. A valid in-memory approval.
2. Delivery of the same event again, without another reservation.
3. A second commit whose acknowledgement is deliberately lost.
4. Replaying that same authorized intent to retrieve its original receipt.

`BEFORE_COMMIT` fault injection returns `RETRYABLE` with no update or receipt.
`AFTER_COMMIT` returns `RECONCILIATION_REQUIRED`, **not success and not a claim
of rollback**. Replaying the bound event retrieves the committed receipt without
spending again. A different actor, tenant, payload or revision cannot use that
reconciliation path.

The store keeps request, budget and receipt changes under one Java monitor and
revalidates optimistic revisions after execution. This teaches a transaction
boundary; it is not database durability, distributed locking or an atomic
transaction spanning Java, SAP and a blockchain. All state disappears when the
demo process exits. A real adapter needs durable idempotency/audit records and
explicit reconciliation for uncertain remote failures.

## Evidence, budgets and errors

The policy uses the existing bounded bytecode host: five seconds including
compilation/startup, one million instructions, a 128 MiB JVM heap and 4096 combined
stdout/stderr bytes. Calls run sequentially in the verification command.
The host is not an OS sandbox or a production multi-tenant execution service.

JSON verification includes expected/Java/DhrLang decisions, worker status,
bounded diagnostics, executed compiler version and source-bundle SHA-256.
Mock receipts include intent, actor/tenant, outcome/reason, before/after state,
revisions and exact budget/reservation values. They are not signed audit records.

Exit codes: 0 for matching verification or the expected simulation sequence,
1 for a failed case/simulation, 2 for file/packaging/worker/interruption errors,
64 for invalid usage. A successful demonstration includes an intentionally
modeled acknowledgement failure; inspect each step's status rather than treating
the CLI's exit code as a real transaction outcome.

## Transfer task and later gates

Explain why a policy decision is not authorization or a committed transaction.
Add a rule and independent positive/negative/boundary fixtures in both languages.
Demonstrate a stale revision and a replay under a different actor or tenant.
For an AI-off held-out exercise, an instructor should supply a rule variant
that is not already implemented in this visible reference.

The separate [LOCAL_CAP_MOCK_ADAPTER](examples/cap-java-mock/README.md) implements
S1 using the actual CAP Java framework and local mock authentication. It reuses
this S0 policy/store without adding CAP dependencies to S0 or connecting it to SAP.
Its toolchain, exact wire contract and API/packaged tests are isolated
from the compiler's build.

Still pending, and **not implemented by either local mock**:

- Authorized tenant/API-specific read-only SAP integration and its mapping,
  pagination and failure tests. The [S2 readiness contract](design/sap-read-only-integration.md)
  lists the non-secret target inputs and separate offline/live acceptance gates.
- Durable storage, real authentication and independent domain/isolation review;
  controlled writes require a separate approval and reconciliation design.
- The measured learner pilot and transfer gates in [LEARNING.md](LEARNING.md).

No SAP credentials, proprietary assets, live deployments or real money are used.
