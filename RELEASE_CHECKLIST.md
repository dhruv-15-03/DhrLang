# DhrLang release checklist

Use this checklist for a new release. Completing a build does not authorize a
tag push or publication. Do not replace an existing release's artifacts.

## 1. Prepare and review

- For the learner-first major release, review the
  [candidate support contract](design/release-support-contract.md) and
  [independent qualification gates](design/learner-preview-gates.md).
  Record the approved component scope and actual evidence; green CI alone does
  not qualify live SAP, AI tutoring, learner outcomes or runtime isolation.
  Resolve or explicitly assess applicable dependency alerts before promotion.

- Align `build.gradle`, `SPEC.md`, `CHANGELOG.md`, the extension package version
  and its lockfile. Use a new version; do not republish 4.0.2 with different bytes.
- Record supported and experimental features accurately in the changelog.
- Review the source commit and all release-workflow changes.
- Before any commit or remote write, verify the active GitHub and Git identities
  are `dhruv-15-03`, with Git email `dhruvrastogi2004@gmail.com`. Clear injected
  `GH_TOKEN`, `GITHUB_TOKEN`, `GIT_CONFIG_PARAMETERS` and `GIT_CONFIG_COUNT` in the
  same shell as an authenticated write. Abort on an identity mismatch.

## 2. Build and verify locally

Requires JDK 17 and Node.js 22+ for the locked VSIX packaging tools. Users of the
extension need Java 17+, not Node.js or the packaging toolchain.

From the repository root in PowerShell:

```powershell
.\gradlew.bat clean check stageCompiler distZip distTar
if ($LASTEXITCODE -ne 0) { throw "Compiler verification failed" }

Set-Location vscode-extension
npm ci
if ($LASTEXITCODE -ne 0) { throw "Dependency installation failed" }
npm run test:packaging
if ($LASTEXITCODE -ne 0) { throw "Packaging tests failed" }
npm run package
if ($LASTEXITCODE -ne 0) { throw "VSIX packaging failed" }
npm run verify:package
if ($LASTEXITCODE -ne 0) { throw "Artifact verification failed" }
Set-Location ..
```

On Linux/macOS, use `./gradlew` instead of `.\gradlew.bat`.

| Path | Purpose |
|---|---|
| `build/libs/DhrLang-<version>.jar` | Standalone fat JAR, from `shadowJar` |
| `build/libs/plain/DhrLang-<version>.jar` | Thin JAR for Maven and the application distribution; not standalone |
| `build/compiler/DhrLang.jar` | Exact fat JAR staged by Gradle, independent of directory ordering |
| `build/release/DhrLang.jar` | Compiler packaged alongside the verified VSIX |
| `build/release/dhrlang-vscode-<version>.vsix` | Extension containing the same compiler bytes |
| `build/release/release-manifest.json` | Versions, source revision and SHA-256 hashes for the JAR and VSIX |

Local builds without a supplied `RELEASE_COMMIT`/CI revision record a null source
revision. Publication requires the exact tagged commit in the manifest.

`check` runs packaged-compiler regressions with no fallback to the test classpath.
It also checks that thin/fat outputs and Maven publication coordinates are distinct.
The crypto test uses the public secp256k1 test scalar 1, never a real wallet,
credentials, RPC endpoint or transaction. It also tests all three JVM backends
and a framed LSP completion request from a temporary directory.

To verify a downloaded JAR without rebuilding that artifact:

```powershell
.\gradlew.bat verifyCompilerArtifact "-PcompilerJar=C:\downloads\DhrLang.jar"
```

## 3. Publish the compiler release

Only `.github/workflows/release.yml` owns compiler-release publication. The CI
workflow tests and builds artifacts but must not publish a second release.

- Obtain approval for the exact new version and commit.
- Push `v<version>` under the verified identity, or manually dispatch `Release`
  with an already-existing `v<version>` tag.
- The requested tag, build version and extension version must agree.
- The workflow runs `check`, packages the canonical VSIX, verifies archive contents
  and attaches the JAR, VSIX, manifest, archives and checksums.
- It refuses to overwrite an existing release.
- It downloads the published JAR and VSIX and reruns their integrity checks.
- Confirm the workflow and the downloaded-artifact checks succeeded before
  announcing the release or starting Marketplace publication.

The application ZIP contains its dependency JARs and launchers. The standalone
JAR and the portable Linux/Windows archives must contain the fat compiler.
`checksums.txt` uses artifact basenames so it can be checked after downloading.
The portable archives also include the linked readiness/support documents,
optional offline evidence tool/tests and the tracked CAP example sources.
The CAP example requires its own pinned toolchain; it is not a production
integration bundled into the standalone compiler.

## 4. Publish the same VSIX to the Marketplace

The compiler release must exist first. `VSCE_PAT` must be available for the
`EnggWithDhruv` publisher. Do not print or store the token in project files.

- Push `vscode-v<version>` pointing to the same source commit as `v<version>`, or
  dispatch `VS Code Extension Release` with `<version>` (without a `v` prefix).
- The workflow downloads the canonical JAR, VSIX and manifest from `v<version>`.
  It does not change package versions, rebuild the compiler or repackage the VSIX.
- It checks source revision, versions, publisher, runtime files, dependency
  presence and the exact embedded-compiler hash before `vsce publish --packagePath`.
- It queries the Marketplace for the published version before creating the
  extension GitHub release. Inspect failed steps instead of assuming a tag means
  the extension was published.
- If Marketplace publication succeeded but indexing/release creation failed,
  inspect the live version before retrying. Never rebuild different bytes with
  the same extension version.

## 5. Post-release evidence

- Download the standalone compiler and install the released VSIX on clean
  supported machines.
- Verify version, a sample program, crypto/signing availability and LSP behavior.
- Confirm the Marketplace version matches the intended compiler release.
- Retain the manifest, checksums and CI results with the release.
- Update public instructions only after the corresponding release exists.

Passing these distribution checks does not certify EVM semantics or incomplete
contract scaffolds for real-money use. Those have separate correctness gates.
