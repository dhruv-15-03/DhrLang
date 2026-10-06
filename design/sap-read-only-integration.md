# Stage 5 S2: read-only SAP integration readiness

**Status: planned, not implemented or verified against SAP.** S0 is an offline
purchase-approval lab; S1 is the actual
[local CAP Java mock adapter](../examples/cap-java-mock/README.md). Neither
establishes SAP connectivity, production authorization or business correctness.
This document defines the next integration gate, not another mock presented as
integration. It does not enable remote calls or change the compiler.

## Target selection is a prerequisite

The repository does not select a live target or assume that credentials are
missing. Before implementing a connector, the system owner must provide these
non-secret facts:

| Input | Required detail |
|---|---|
| System | Product/edition and environment: authorized sandbox, development or test; not an assumed production tenant |
| API contract | Official API identifier, version, protocol and relevant entity sets/operations |
| Read scope | Approved operations and fields; tenant/company/cost-center boundaries; confirmation that access is permitted |
| Authentication route | Supported destination/service-binding or SDK authentication mechanism; how the authenticated actor and tenant are established |
| Mapping | Entity keys, supplier status, currency, amount units/scale, workflow status, revision semantics and ownership of budget data |
| Test data | Synthetic or approved sanitized records, including multi-page, empty, inaccessible and boundary cases |
| Operational limits | Approved request rate, page/result limits, timeouts and retry rules |
| Reviewer | A system/domain owner who can confirm mappings and authorization expectations |

Do not paste tokens, passwords, service keys, private binding files or personal
business records into chat, fixtures or Git. Provision access through the
selected environment's approved mechanism only after the target is agreed.
An API sandbox may test transport and mappings without exercising a customer's
tenant authorization; record that distinction rather than claiming both.

## Implementation boundary

Keep the connector in an isolated adapter module, not the compiler/runtime.
Select its supported SAP SDK/client and pin versions against the named API
contract. Reuse the vendor's supported authentication, destination and protocol
handling rather than implementing a new OAuth or OData stack.

S2 reads data only. It must not call an approval action, reserve a budget,
create/update/delete a business entity or turn an S0 mock receipt into a remote
transaction. The DhrLang worker receives validated, minimal decision inputs:
no credentials, arbitrary URLs, remote client, writable store or caller-supplied
policy source. A proposed decision remains advisory.

Derive actor and tenant from trusted authentication. Do not extend S1's public
mock identities into live authentication or accept role/tenant claims from
request fields. Fix the destination and operation allowlist server-side; never
follow a caller-selected endpoint or a pagination URL outside the approved
destination. Enforce authorization on every page and result path.

## Mapping and failure contract

The existing [S0 numeric profile](../ENTERPRISE_LAB.md) remains unchanged.
Validate exact integer minor units and all intermediate totals/deltas against
that profile. Do not convert decimal money through `double`, truncate fractions,
infer currency scale or silently perform FX conversion. If the selected API
returns decimal amounts, an explicit currency/scale mapping and lossless
conversion contract are required; unrepresentable inputs fail visibly.

Do not treat arbitrary SAP ETags or revision strings as S0 numeric revisions.
Retain the remote token as an opaque value unless the API explicitly defines a
compatible numeric contract. Distinguish remote observation/version evidence
from the local mock workflow's revisions.

Pagination is bounded and complete-or-explicitly-incomplete. Detect repeated
continuations; reject malformed or off-destination links. A page failure must
not produce a successful partial budget/eligibility decision. Missing fields,
unknown statuses, ambiguous units, inaccessible records and stale observations
are explicit mapping/access/conflict outcomes, not defaults to zero or approval.

Retries apply only to the approved read operations and documented transient
conditions, with a total time/attempt budget. Authentication/authorization,
mapping and invalid-input failures are not retried into success. An upstream
timeout or outage is unavailable evidence, not a rejected business request or
an invented successful read.

Logs and reports retain bounded operation, mapping version and failure evidence,
without credential-bearing headers, raw business payloads or sensitive query
strings. Agree retention and redaction with the system owner.

## Acceptance matrix

These are required tests, **not results already obtained**.

| Boundary | Offline contract evidence | Authorized target evidence |
|---|---|---|
| API/version | Sanitized schema/response fixtures and strict DTO mapping | Named supported endpoint responds with the expected contract |
| Authentication | Missing/invalid context fails closed; no caller role override | Approved authentication route works; denied access remains denied |
| Tenant/scope | Cross-tenant and cost-center access tests, including subsequent pages | System owner verifies actual tenant/scope enforcement |
| Pagination | Empty/multi-page, duplicate continuation, off-destination link, page/count/time limits | Real multi-page read completes or reports its explicit limit |
| Numeric mapping | Fractional/overflow/unit/currency cases; exact profile-boundary values and totals | Approved records agree with independently checked units/values |
| Revisions | Opaque tokens retained; no numeric coercion or stale-observation success | Real API token/version behavior is captured |
| Failures | 401/403/404/429/5xx, malformed response, timeout, interrupted pagination | Permitted failure/denial checks; do not induce an outage |
| Worker boundary | Real bounded runner, diagnostic/timeout handling and host revalidation | Same adapter path consumes sanitized authorized observations |
| Read-only policy | Fixed operation allowlist; no write route; no arbitrary destination | Owner reviews scope and records that no write was performed |

Offline fixture tests can be implemented once a concrete public API contract is
selected, without live credentials. They must remain labeled offline. Local
CAP success does not satisfy the right-hand column.

## Exit and later gates

S2 is complete only when the selected authorized target, mapping and read-only
failure/access evidence are recorded and reviewed. No live test result, SAP
certification, durable store or production support is claimed by this document.

S3 remains separate: choose deployment/storage, real authorization and write
policy; implement durable idempotency/audit and reconciliation; obtain independent
review and explicit approval before any real write. Learner pilot, domain review,
isolation review and release promotion remain the roadmap's human qualification
gates. No new release or version change follows automatically from S2.
