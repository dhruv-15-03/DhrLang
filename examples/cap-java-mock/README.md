# LOCAL_CAP_MOCK_ADAPTER

This is Stage 5 **S1**: an isolated, actual CAP Java service around DhrLang's S0
purchase-approval lab. It uses CAP's OData adapter, generated CDS Java interfaces,
event handlers and mock authentication. It is **not a SAP tenant connection,
durable transaction service, public hosting configuration or production
qualification**.

The root DhrLang Gradle build/runtime is unchanged. This example has its own
Maven/CDS dependency graph and is not embedded in the compiler or VS Code package.
It is an unreleased source-build example, absent from published v4.0.2 binaries.

## Pinned toolchain

| Component | Pin |
|---|---|
| Adapter Java | Java 25; verified starting toolchain 25.0.2 |
| Compiler Java | Existing Java 17 Gradle toolchain, unchanged |
| CAP Java SDK / CDS Maven plugin | 5.1.1 |
| Spring Boot | 4.1.1, as referenced by the published CAP 5.1.1 framework POM |
| Maven | 3.9.14 through the checksum-pinned wrapper |
| Local Node | 22.16.0 through the CDS Maven plugin |
| CDS development kit | 10.1.0, exact package version and lockfile |
| Maven/test/server starting heaps | 256 MiB, sequential tests; bounded DhrLang workers use their existing limits |

This selection follows [SAP's Java prerequisites](https://cap.cloud.sap/docs/java/getting-started),
the [CAP build/typed-generation guide](https://cap.cloud.sap/docs/java/developing-applications/building),
and the [published 5.1.1 framework POM](https://repo.maven.apache.org/maven2/com/sap/cds/cds-framework-spring-boot/5.1.1/cds-framework-spring-boot-5.1.1.pom).
CAP's current documented minimums are Java 21 and Maven 3.9.14; this example
deliberately pins Java 25 instead of changing DhrLang's own language toolchain.

Maven, Node, npm caches and generated sources stay under this example and are
ignored by Git. No global CDS installation, SAP account or service binding is
required. Dependencies need network access on first setup; service tests
themselves use no remote SAP/BTP service.

SAP and other dependencies retain their own licenses. They are downloaded by
their normal package managers, not vendored into this repository or represented
as DhrLang-owned SDK code.

## Build and verify

With Java 25 selected in `JAVA_HOME`, from the repository root in PowerShell:

```powershell
.\examples\cap-java-mock\verify.ps1
```

The script:

1. Builds/stages the existing compiler and its thin source API/POM; it does not
   rerun S0 tests merely as setup.
2. Installs the thin API with the `local-source` classifier into this example's
   local Maven repository. An old published 4.0.2 artifact is not a substitute.
3. Runs the pinned CDS generation, CAP framework tests and repackaged-JAR tests.

On Linux/macOS, PowerShell 7 (`pwsh`) can run the same script after making
`gradlew` and `examples/cap-java-mock/mvnw` executable. CI exercises that route.
The script restores the environment variables and working directory it changes.
It does not publish a Maven artifact, create a release or change global tools.

The handler uses the existing `PurchaseWorkflow`, reference policy and bounded
`PurchaseDecisionRunner`, not a rewritten approximation. Startup checks the API
and standalone compiler's version, complete DhrLang class set and class bytes.
The check supports the API nested under `BOOT-INF/lib` in the executable Boot
JAR without unpacking arbitrary input. A same-version, different-byte compiler
is rejected.

## Local API

For manual experimentation, after verification:

```powershell
Set-Location examples\cap-java-mock
$compiler = (Resolve-Path ..\..\build\compiler\DhrLang.jar).Path
java -Xms32m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC `
  -jar srv\target\local-cap-mock-service-0.1.0-SNAPSHOT-local-mock.jar `
  "--dhrlang.compiler-jar=$compiler" --server.port=8080
```

Only loopback addresses are accepted. Non-local profiles, cloud-binding
environment variables, default privileged mock users and weakened authentication
settings fail startup. Port 0 is the default for automatically assigned local
ports; the example command chooses 8080. Do not expose or proxy this service
publicly.

The OData root is `/odata/v4/purchase-lab`. `GET .../$metadata` returns the real
CAP service definition. `GET .../snapshot(requestId='purchase-001')` returns the
authenticated tenant's scoped fixture state. `POST .../approve` accepts:

```json
{
  "command": {
    "eventId": "approval-001",
    "requestId": "purchase-001",
    "requestVersion": "0",
    "budgetVersion": "0"
  }
}
```

**Revisions are canonical decimal strings in both input and output**, including
snapshot and receipt fields. They are parsed exactly into S0's unchanged `long`
values. JSON numbers, fractional/exponent forms, signs, whitespace, leading zeros
and overflow are rejected for command revisions. This avoids accidental
JavaScript rounding above the safe-number range without requiring an
`IEEE754Compatible` header or a special client.

The initial Integer64 input design was rejected during testing because CAP accepts
mathematically integral `0.0`. The explicit string contract retains token
strictness without fragile pre-OData request filters or batch-specific bypasses.
Tests round-trip actual API strings around `9007199254740993` through a snapshot,
review receipt and subsequent approval, asserting JSON value types as well as
exact values.

Monetary values still use S0's bounded exact-integer minor-unit profile. They are
owned by the host fixture store, not accepted as action overrides. No currency
conversion, amount truncation or VM numeric change is introduced.

## Mock identity is not real authentication

The local profile defines public **test fixtures**, all with the non-secret test
password `local-test-only`. These are not real accounts or credentials:

| Mock user | Tenant | Purpose |
|---|---|---|
| `bob-a` | `training-a` | Authorized approver |
| `alice-a` | `training-a` | Request owner; self-approval must fail |
| `reviewer-a` | `training-a` | Lower approval limit; sends eligible requests to review |
| `sales-a` | `training-a` | Wrong cost-center scope |
| `viewer-a` | `training-a` | Lacks the CAP `Approver` role |
| `bob-b` | `training-b` | Separate tenant and budget |
| `unmapped-a`, `foreign` | Mock contexts without a matching host grant | Must fail closed |

CAP authenticates these fixtures and populates `UserInfo`. The adapter derives
actor, tenant and CAP role from that context, then applies server-owned grants.
Caller-supplied roles, tenant headers, actor fields or proposed outcomes cannot
grant authority. Default CAP `privileged`/`system` mock users are disabled.

The seeded stores are in memory, with independent tenant state. The S0
actor/tenant/payload/revision bindings and host policy checks remain authoritative.
There is no HTTP endpoint for altering grants, injecting faults, supplying
policy source or selecting compiler executables.

`.cdsrc-private.json`, `default-env.json` and local `.env` variants are ignored.
Only deliberately sanitized `.env.example`/`.env.sample` files are eligible for
tracking. Do not configure real service bindings in this example, paste secrets
into requests or commit them.

## Errors and actual framework evidence

Valid application outcomes (`APPLIED`, `REJECTED`, `REPLAYED`) return a DTO labeled
`LOCAL_CAP_MOCK_ADAPTER`. Denials and failed operations are HTTP errors:

| Condition | HTTP |
|---|---|
| Missing/invalid mock authentication | 401 |
| CAP role or host tenant/actor/scope denial | 403 |
| Malformed typed input | 400 |
| Stale version or incompatible replay intent | 409 |
| Invalid/failed proposal or disagreement with host policy | 502 |
| Actual bounded worker timeout | 504 |
| Missing bridge artifact, pre-commit retry or uncertain acknowledgement | 503 |

Custom CAP error codes distinguish `STORE_RETRYABLE` from
`RECONCILIATION_REQUIRED`. The latter never claims success or rollback. CAP's
error/rollback handling does **not** undo an already applied in-memory S0 commit;
the tests prove that replay retrieves its original receipt without another
reservation.

The suite exercises the real CAP OData/authentication pipeline, not just direct
calls to plain Java. Positive cases use the actual DhrLang worker. Error cases
include real malformed/incorrect DhrLang programs, a real worker time limit,
explicit S0 storage-fault simulation, tenant spoof attempts and access checks on
reconciliation paths.

Failsafe tests also launch the **actual repackaged `java -jar` application**,
verify loopback HTTP readiness and action/revision round trips, and reject
non-loopback startup and same-version compiler byte mismatches. Owned test
processes are stopped after each test.

## Still outside this unit

No real SAP/BTP login, service call, durable production store, real business
transaction, public hosting, learner pilot or independent qualification has been
performed. S2 requires a selected authorized target/API and approved access.
S3 durability/controlled writes require separate deployment/policy decisions and
review. The root [enterprise lab](../../ENTERPRISE_LAB.md) and
[learner qualification gates](../../LEARNING.md) continue to apply.

Implementation references:
[CAP mock users](https://cap.cloud.sap/docs/java/security#mock-users),
[CAP service tests](https://cap.cloud.sap/docs/java/developing-applications/testing),
[CDS constraints](https://cap.cloud.sap/docs/guides/services/constraints),
[Boot nested JARs](https://docs.spring.io/spring-boot/specification/executable-jar/jarfile-class.html).
