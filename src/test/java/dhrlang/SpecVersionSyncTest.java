package dhrlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures the {@code Version:} line in SPEC.md stays in sync with the project version.
 *
 * <p>The version is read from {@code build.gradle}, which is always present in the working
 * tree, rather than from the JAR manifest. An earlier version of this test read
 * {@code getImplementationVersion()} and returned early when it was {@code null} - which is
 * always the case when running from a classpath rather than a packaged JAR. That made the
 * test report success without asserting anything during a normal {@code ./gradlew test}.
 */
public class SpecVersionSyncTest {

    @Test
    void specVersionMatchesBuildVersion() throws IOException {
        String buildVersion = readBuildGradleVersion();
        assertNotNull(buildVersion, "Could not find a version declaration in build.gradle");

        String specVersion = readSpecVersion();
        assertTrue(specVersion.startsWith(buildVersion),
                () -> "SPEC.md version (" + specVersion + ") does not match build.gradle (" + buildVersion + ")");
    }

    /** Cross-check: when running from a packaged JAR the manifest must agree too. */
    @Test
    void manifestVersionMatchesBuildVersionWhenPackaged() throws IOException {
        Package pkg = dhrlang.Main.class.getPackage();
        String implVersion = pkg == null ? null : pkg.getImplementationVersion();
        if (implVersion == null) {
            // Running from a classpath, not a packaged JAR: there is no manifest to check.
            // This is a genuine no-op rather than a silent pass of the assertion above.
            return;
        }
        assertEquals(readBuildGradleVersion(), implVersion,
                "JAR manifest Implementation-Version disagrees with build.gradle");
    }

    private String readBuildGradleVersion() throws IOException {
        for (String line : Files.readAllLines(Path.of("build.gradle"))) {
            String trimmed = line.trim();
            if (trimmed.startsWith("version")) {
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.charAt(0) == '\'' || value.charAt(0) == '"')) {
                    return value.substring(1, value.length() - 1);
                }
            }
        }
        return null;
    }

    private String readSpecVersion() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("SPEC.md"));
        String versionLine = lines.stream()
                .filter(l -> l.startsWith("Version:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Version line missing in SPEC.md"));
        return versionLine.replaceFirst("Version:\\s*", "").trim();
    }
}

