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
dependency alerts (critical/high/moderate); the authenticated details API initially denied
access, so that assessment did not infer alert identities from CI or local scans.
Independent npm audit identified vulnerable extension test dependencies and CAP
tooling dependencies. Pinning the extension's development-only Mocha to 12.0.3
removed its vulnerable dependency chains; a fresh locked install and audit
reported zero advisories for that lockfile. This does not establish closure of
the GitHub alerts or clear the separate CAP tooling lockfile.
In the pinned CAP 10.1.0 tooling, `proxy-addr` is affected
by [GHSA-jqcg-44mw-7w3h](https://github.com/advisories/GHSA-jqcg-44mw-7w3h),
and `braces` by [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
During this readiness assessment, proxy-addr 2.0.8 was published, but the latest
braces 3.0.3 remained affected. Do not force a CAP major-toolchain downgrade or
override bundled dependencies without checking compatibility and the actual CAP
API/packaged tests. Assess remediation and applicability before promotion rather
than treating green CI as security clearance.

The 9 October authorized refresh succeeded and reported one open Dependabot
alert: number 10, proxy-addr in `examples/cap-java-mock/package-lock.json`. A targeted
`@sap/cds-dk` npm override to 2.0.8 was tested locally: npm reported an
up-to-date install but the actual bundled dependency remained 2.0.7. The
ineffective override was removed; no CAP lockfile/runtime change is claimed.
The independent npm audit additionally reports braces and newly listed
handlebars advisories (including
[GHSA-8r5x-fm3f-whwj](https://github.com/advisories/GHSA-8r5x-fm3f-whwj)).
The registry publishes handlebars 4.7.10, but its presence does not prove the
bundled CAP dependency graph has been upgraded.
GitHub's open count and npm's affected-package count are different evidence,
not interchangeable. CAP tooling remains a release assessment/remediation gate;
no clean audit, alert closure or supported-major approval is asserted.

### Dependency reassessment (13 October 2026)

The read-only reassessment used source
`f68258a2c655bdefa4f0f4c7f8b472fe2bcbf827`, still the remote main head, and
the unchanged CAP lockfile with SHA-256
`9187c3d4450ab5b0b13a6e18b6c4c71cca8fa701b8583656ffa4a466c5583a22`.
The authenticated GitHub account was verified as `dhruv-15-03`. GitHub now
reports three open dependency alerts for that lockfile: critical proxy-addr
(10), critical handlebars (13), and medium handlebars (14). These are dependency
alerts, not a recurrence of the original sample-contract analyzer findings.

The installed nested graph still contains CDS DK 10.1.0, proxy-addr 2.0.7,
handlebars 4.7.9 and braces 3.0.3. Registry metadata still names CDS DK 10.1.0
and braces 3.0.3 as latest; proxy-addr 2.0.8 and handlebars 4.7.10 are available
separately but are not present in the bundle. The independent locked audit
reports seven affected packages (three critical, four high), including parent
dependency chains; this is not seven distinct GitHub alerts.

The audit's suggested CDS DK 9.9.6 downgrade was not applied. The unbundled
`@sap/cds` 10.1.1 package exposes `cds-serve` and `cds-deploy`, not the `cds`
executable used by this example's configured `build --for java` step. Replacing
the toolkit with that package alone is therefore not a drop-in fix. Neither
editing installed vendor files nor repeating the ineffective bundle override
establishes a reproducible remediation.

From the repository root, the bounded diagnostic commands were:

```powershell
$env:NODE_OPTIONS = '--max-old-space-size=128'
npm.cmd --prefix .\examples\cap-java-mock audit --package-lock-only --ignore-scripts --json --fetch-retries=0 --fetch-timeout=20000
npm.cmd --prefix .\examples\cap-java-mock ls '@sap/cds-dk' proxy-addr braces handlebars --all --depth=5
```

The audit completed in 23.14 seconds with exit 1 for findings; installed-version
inspection completed with exit 0. The Node limit bounds old-space, not total
process memory. The starting shared-resource snapshot had 679 MiB free of
16,088 MiB and no Java process. No JVM/CAP build, test server, server PID or
heavy-resource reservation was started, and dependencies were not changed.
A compatible upstream fix or a separately assessed build-tool replacement
still needs actual framework and executable-JAR verification after a current
resource grant. Earlier CAP results are historical evidence, not a new run.
Learner/domain/isolation evidence, component/support scope and publication
approval remain separate unresolved gates.

### Modular CAP tooling candidate (13 October 2026)

A narrower compatible build is now implemented for the local mock's fixed
model: unbundled `@sap/cds` 10.1.0 and `@sap/cds-compiler` 7.1.0, the same
model/runtime and compiler versions used by the original development-kit build.
SAP's Java transformation and CSN/EDMX APIs generate the model consumed by the
Maven plugin's existing Java-interface generator. General development-kit
commands and deployment features are not claimed.

A regenerated lockfile and fresh `npm ci --ignore-scripts --no-audit --no-fund`
install contain 70 packages, with actual proxy-addr 2.0.8 and no development kit,
braces or handlebars. The locked npm audit now reports zero findings; GitHub
alert closure is not inferred from this local result. Four model regressions
pass, including failure and stale-output handling. Both generated files match
the original Java-build outputs byte-for-byte:

| Generated artifact | Original and candidate SHA-256 |
|---|---|
| `csn.json` | `ad6fe8b8b476a8e9e26410a9187a5e3bef5cf2159fe948d80a56c75698126136` |
| `odata/v4/PurchaseLab.xml` | `e1dca514c08abf4f9647a5fee3c4cdd8760ae1288f0ee0e0cd4e6bb37806587c` |

The fresh locked install/version inspection took 35.99 seconds; the successful
sequential model tests/build/byte comparison took 4.58 seconds. Node old-space
was limited to 128 MiB. The Maven build now runs the locked audit and model
regressions before model generation instead of executing the bundled CLI.
Actual framework/executable-JAR verification and exact-head CI are still required
before treating a candidate as a completed remediation. When the shared local
host cannot safely accommodate the existing sequential 256 MiB JVM cycle,
validation can use the already-existing isolated CAP workflow with authorization.
Retain that run's exact candidate SHA and actual framework/JAR results; older
baseline runs do not qualify a changed build. Neither path establishes human
qualification or release/public-hosting approval.

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
