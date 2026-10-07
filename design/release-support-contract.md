# Candidate release support contract

Status: release preparation, not approval for a supported 5.0.0 release.
The reviewed source baseline is `a86eb131678e36188974ee30663417aef2f36cc9`.
Development builds still identify themselves as 4.0.2; they must not replace
the different, already-published 4.0.2 artifacts.

## Component scope

| Component | Candidate scope and evidence | Claims not established |
|---|---|---|
| Standalone compiler and extension | Java 17+ compiler; extension engine minimum in its package manifest; exact compiler/VSIX hash and version checks | Universal OS/JVM compatibility or universal backend equivalence |
| Local projects | Ordered manifest v1 with exact compiler version and global declarations; see [PROJECTS.md](../PROJECTS.md) | A package registry, scoped module contract or automatic dependency resolution |
| Host and learner commands | Local bytecode profile v1, bounded worker processes, ten exercises, historical reports and traces; see [HOST_EXECUTION.md](../HOST_EXECUTION.md) and [LEARNING.md](../LEARNING.md) | An OS sandbox, hostile multi-tenant safety or measured learning advantage |
| Purchase-approval lab | Synthetic domain fixtures and host-owned in-memory workflow; see [ENTERPRISE_LAB.md](../ENTERPRISE_LAB.md) | Production money handling, durable business transactions or independently reviewed domain correctness |
| CAP example | Separate actual local CAP framework mock with its own pinned toolchain and loopback tests | Live SAP/BTP qualification, customer authentication or production hosting |
| Evidence exporter | Optional Python 3.12 standard-library local handoff; no model or source execution | An AI tutor, enforced provider budgets, authenticated reports or learner qualification |
| EVM target | Experimental independent-node regression corpus | Financial certification or authorization to deploy real money |

The JVM compiler and the CAP example have different toolchains. Do not require
the CAP JDK/Maven/Node toolchain merely to run the standalone compiler. Python
is required only for the optional exporter. Published 4.0.2 binaries do not gain
new commands when current source documentation changes.
Portable archives include tracked CAP example sources for reference, not the
whole compiler source/build checkout or downloaded dependencies. Building that
example still requires a full checkout of the matching source revision and its
documented isolated setup; copying it does not establish new integration evidence.

## Compatibility and migration

This release preparation introduces no new numeric defaults, host schema
version, project schema version or bytecode format. Existing JVM signed-64-bit
and floating-point behavior is not a checked-money contract. Preserve failure
statuses, exact-output grading and host-side authority checks.

For an eventual new compiler version:

1. Align the compiler, specification, changelog and extension manifests through
   the existing release-consistency gate. Review newer source features separately
   rather than assuming the old Stage 3 design covers every current parser path.
2. Recheck existing single-file programs on the exact new artifact. For project
   manifests, deliberately update the exact `compilerVersion` pin only after
   replaying that project's check/run fixtures; do not silently accept any version.
3. Retain old artifact hashes and original manifests. Historical learner reports
   remain bound to their recorded source/version and are not evidence that the
   new artifact or an edited submission passes.
4. Revalidate the CAP API/worker byte-identity guard if updating its compiler
   dependency. Do not bypass it to make an old adapter load a different worker.

Additive commands alone do not establish the reason for a major version.
The maintainer must approve either a justified public 5.0 compatibility/support
contract or an additive 4.x version before version changes or publication.

## Qualification and promotion evidence

The [learner-preview gates](learner-preview-gates.md) define the still-required
cohort, baseline, held-out evaluation and independent reviews. Keep failed setups,
dropouts and unavailable results in their denominators. Synthetic tests and
agent-produced review notes do not fulfill independent participation gates.

Live SAP and AI tracks may be excluded from a scoped candidate, but only by an
explicit scope decision. Exclusion does not waive learner/domain/isolation gates
or authorize production claims for the remaining components.

Before approval, retain the exact source SHA, artifact/VSIX hashes, dependency
inventory, all required workflow outcomes, explicit skips, reviewer findings and
supported-installation evidence. The readiness push `b2fd0d4` reported three
dependency alerts (critical/high/moderate); the authenticated details API denied
access, so those alert identities are not inferred from CI or local scans.
Independent npm audit identified vulnerable extension test dependencies and CAP
tooling dependencies. Pinning the extension's development-only Mocha to 12.0.3
removed its vulnerable dependency chains; a fresh locked install and audit
reported zero advisories for that lockfile. This does not establish closure of
the inaccessible GitHub alerts or clear the separate CAP tooling lockfile.
In the pinned CAP 10.1.0 tooling, `proxy-addr` is affected
by [GHSA-jqcg-44mw-7w3h](https://github.com/advisories/GHSA-jqcg-44mw-7w3h),
and `braces` by [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
During this readiness assessment, proxy-addr 2.0.8 was published, but the latest
braces 3.0.3 remained affected. Do not force a CAP major-toolchain downgrade or
override bundled dependencies without checking compatibility and the actual CAP
API/packaged tests. Assess remediation and applicability before promotion rather
than treating green CI as security clearance.

## Failure reporting and rollback

Reports should identify the exact artifact/version, OS/Java, command, profile,
exit/status and a minimal redacted reproduction. Never include credentials,
customer transactions or unreviewed learner outputs. A successful worker process
exit does not override a failed structured execution status.

If an approved release regresses, stop recommending the affected component and
install the previous immutable compiler/VSIX pair using its retained hashes.
Restore its matching project pins and replay local fixtures. Do not replace
published bytes under an existing version, downgrade a durable store blindly,
or assume the local CAP mock provides a production rollback procedure.

Named support ownership, supported-platform coverage, incident response and any
live-store migration/rollback policy still require maintainer decisions. This
document describes the evidence needed; it does not promise an SLA.
