# DhrLang Release Checklist

## Pre-Release Steps

### 1. Version Management
- [ ] Update `version` in `build.gradle` to the target release version (currently `1.1.8`)
- [ ] Ensure version matches any references in documentation

### 2. Build and Test
```powershell
# Clean build with all tests
./gradlew.bat clean test shadowJar --no-daemon

# Verify test results
# All tests should pass (except expected skipped tests)
```

### 3. Verify Production Artifact
```powershell
# Check the JAR exists
ls build\libs\DhrLang-*.jar

# Test version
java -jar build\libs\DhrLang-1.1.6.jar --version

# Test help
java -jar build\libs\DhrLang-1.1.6.jar --help

# Test a working program
java -jar build\libs\DhrLang-1.1.6.jar input\simple_working_test.dhr

# Test JSON diagnostics (should output clean JSON only)
java -jar build\libs\DhrLang-1.1.6.jar --json --time input\parser_error_test.dhr
```

### 4. Quality Checks
- [ ] All tests passing (146+ tests)
- [ ] CLI options work correctly
- [ ] JSON output is clean (no banners mixed in)
- [ ] Version is correct in `--version` output
- [ ] No critical Gradle deprecations from our own scripts

## Release Steps

### 1. Create Git Tag
```bash
git tag -a v1.1.6 -m "Release version 1.1.6"
git push origin v1.1.6
```

### 2. Create GitHub Release
1. Go to: https://github.com/dhruv-15-03/DhrLang/releases/new
2. Choose tag: `v1.1.6`
3. Release title: `DhrLang v1.1.6`
4. Description: Include highlights from `CHANGELOG.md`
5. Upload assets:
   - `build/libs/DhrLang-1.1.6.jar` (required)
   - Optional: Create a ZIP with `lib/DhrLang-1.1.6.jar` and `LICENSE`

### 3. Verify Release
- [ ] Download the release JAR from GitHub
- [ ] Test it on a clean machine (if possible)
- [ ] Verify README instructions work with the released artifact

## Post-Release

### 1. Documentation
- [ ] Update `CHANGELOG.md` with release date
- [ ] Consider updating badges if version is shown anywhere

### 2. Communication
- [ ] Announce release (if applicable)
- [ ] Update any external documentation or project pages

## Current Release Status (v1.1.6)

### ✅ Completed
- Build configuration finalized (`shadowJar` produces fat JAR)
- Manifest attributes set correctly (`Main-Class`, `Implementation-Version`)
- JSON diagnostics contract documented and tested
- CLI options documented in README
- Gradle script deprecations fixed (our own code)
- All tests passing
- JSON-only output verified (no banner contamination)
- README updated with production instructions

### 📋 Ready for Release
- Version: `1.1.6`
- Artifact: `build/libs/DhrLang-1.1.6.jar` (approx ~4-6 MB with dependencies)
- Java requirement: Java 17+
- Platforms: Windows, Linux, macOS (JVM-based)

### 🔄 Future Improvements (Not Blockers)
- Plugin deprecations (Shadow, SpotBugs) - wait for plugin updates
- Raise Jacoco coverage thresholds as test coverage improves
- Add platform-specific launcher scripts (e.g., `dhr.bat`, `dhr.sh`)
