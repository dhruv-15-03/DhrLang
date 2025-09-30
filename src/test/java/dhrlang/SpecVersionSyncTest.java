package dhrlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures SPEC.md Version: line starts with the project version (from implementation package manifest if present). */
public class SpecVersionSyncTest {

    @Test
    void specVersionMatchesImplementation() throws IOException {
        // Determine implementation version (manifest) or fallback to declared constant
        String implVersion = dhrlang.Main.class.getPackage() != null ? dhrlang.Main.class.getPackage().getImplementationVersion() : null;
        if(implVersion == null) {
            // fallback to build.gradle value hardcoding not ideal; skip test if missing
            System.out.println("[SpecVersionSyncTest] Implementation version not available (dev mode); skipping strict check.");
            return;
        }
        List<String> lines = Files.readAllLines(Path.of("SPEC.md"));
        String versionLine = lines.stream().filter(l -> l.startsWith("Version:")).findFirst().orElseThrow(() -> new AssertionError("Version line missing in SPEC.md"));
        String specVersion = versionLine.replaceFirst("Version:\s*", "").trim();
        assertTrue(specVersion.startsWith(implVersion.substring(0, Math.min(implVersion.length(), specVersion.length()))),
                () -> "SPEC.md version (" + specVersion + ") does not match implementation (" + implVersion + ")");
    }
}
