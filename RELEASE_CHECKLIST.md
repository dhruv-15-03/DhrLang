# DhrLang Release Checklist

## Pre-Release Steps

### 1. Version Management
- [x] Update `version` in `build.gradle` to `2.0.0`
- [x] Update `SPEC.md` version to `2.0.0`
- [x] Update `README.md` version references to `2.0.0`
- [x] Update `vscode-extension/package.json` version to `2.0.0`
- [x] Ensure `verifySpecVersion` task passes (SPEC.md matches build.gradle)

### 2. Build and Test
```powershell
# Clean build with all tests
./gradlew.bat clean test shadowJar --no-daemon

# Verify test results
# All 1,034 tests should pass with 0 failures
```

### 3. Verify Production Artifact
```powershell
# Check the JAR exists
ls build\libs\DhrLang-*.jar

# Test version
java -jar build\libs\DhrLang-2.0.0.jar --version

# Test help
java -jar build\libs\DhrLang-2.0.0.jar --help

# Test a working program
java -jar build\libs\DhrLang-2.0.0.jar input\sample.dhr

# Test JSON diagnostics (should output clean JSON only)
java -jar build\libs\DhrLang-2.0.0.jar --json --time input\demo.dhr
```

### 4. Quality Checks
- [x] All 1,034 tests passing (0 failures)
- [x] CLI options work correctly (`--help`, `--version`, `--json`, `--time`, `--no-color`, `--backend`, `--emit-ir`, `--emit-bc`)
- [x] JSON output is clean (no banners mixed in)
- [x] Version is correct in `--version` output ("DhrLang version 2.0.0")
- [x] Shadow JAR, Javadoc JAR, and Sources JAR all build successfully
- [ ] No critical Gradle deprecations from our own scripts

## Release Steps

### 1. Create Git Tag
```bash
git tag -a v2.0.0 -m "Release version 2.0.0 - Major release with 7 iterations"
git push origin v2.0.0
```

### 2. Create GitHub Release
1. Go to: https://github.com/dhruv-15-03/DhrLang/releases/new
2. Choose tag: `v2.0.0`
3. Release title: `DhrLang v2.0.0 — Major Release`
4. Description: Include highlights from `CHANGELOG.md` and `RELEASE_NOTES.md`
5. Upload assets:
   - `build/libs/DhrLang-2.0.0.jar` (fat JAR, ~1.3 MB)
   - `build/libs/DhrLang-2.0.0-javadoc.jar`
   - `build/libs/DhrLang-2.0.0-sources.jar`

### 3. Verify Release
- [ ] Download the release JAR from GitHub
- [ ] Test it on a clean machine (if possible)
- [ ] Verify README instructions work with the released artifact

## Post-Release

### 1. Documentation
- [x] Update `CHANGELOG.md` with all 7 iterations
- [x] Update `RELEASE_NOTES.md` with v2.0.0 section
- [ ] Consider updating badges if version is shown anywhere

### 2. Communication
- [ ] Announce release (if applicable)
- [ ] Update any external documentation or project pages

## Current Release Status (v2.0.0)

### Completed
- Build configuration finalized (`shadowJar` produces fat JAR ~1.3 MB)
- Manifest attributes set correctly (`Main-Class`, `Implementation-Version`)
- JSON diagnostics contract documented and tested
- CLI options documented in README
- All 1,034 tests passing (0 failures)
- 3 JARs generated: fat JAR, javadoc, sources
- Version aligned across build.gradle, SPEC.md, README.md, vscode-extension

### Release Artifacts
- **Version**: `2.0.0`
- **Fat JAR**: `build/libs/DhrLang-2.0.0.jar` (~1.3 MB)
- **Javadoc**: `build/libs/DhrLang-2.0.0-javadoc.jar` (~5.1 MB)
- **Sources**: `build/libs/DhrLang-2.0.0-sources.jar` (~292 KB)
- **Java requirement**: Java 17+
- **Platforms**: Windows, Linux, macOS (JVM-based)

### Feature Summary (7 Iterations)
1. Enhanced Error Reporting — unique error codes, contextual hints
2. Smart Contract Safety — view/pure checks, reentrancy analysis, storage layout
3. EVM Backend — opcodes, assembler, ABI encoding, bytecode optimizer
4. Interactive Debugging — breakpoints, debug sessions, REPL, source maps
5. Testing & Verification — fuzzing, property-based testing, coverage, gas profiling
6. Production & Deployment — audit reports, doc generation, multi-chain deploy
7. AI Agent & Data Pipeline — agent orchestration, planning, streaming pipelines

### Future Improvements (Not Blockers)
- Plugin deprecations (Shadow, SpotBugs) — wait for plugin updates
- Raise Jacoco coverage thresholds as test coverage improves
- Add platform-specific launcher scripts (e.g., `dhr.bat`, `dhr.sh`)
