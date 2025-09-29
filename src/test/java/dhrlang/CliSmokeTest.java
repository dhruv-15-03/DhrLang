package dhrlang;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic smoke tests for the CLI flags (--help, --version, --json).
 * Uses a direct process spawn to ensure Main argument parsing path identical to real usage.
 */
public class CliSmokeTest {

    private static final String JAVA = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

    private String runProcess(String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(baos);
        }
        int exit = p.waitFor();
        String out = baos.toString(StandardCharsets.UTF_8);
        return exit + "\n" + out; // exit code on first line for easy parsing/assert
    }

    @Test
    void helpPrintsUsage() throws Exception {
        String jarName = locateJar();
        String result = runProcess(JAVA, "-jar", jarName, "--help");
        assertTrue(result.contains("Usage: java -jar DhrLang.jar"), "Help output should contain usage line. Got: " + result);
    }

    @Test
    void versionPrintsSemanticVersion() throws Exception {
        String jarName = locateJar();
        String result = runProcess(JAVA, "-jar", jarName, "--version");
        assertTrue(result.matches("(?s).*DhrLang version .*"), "Version output missing. Got: " + result);
    }

    @Test
    void jsonModeOutputsJsonOnError() throws Exception {
        String jarName = locateJar();
        // Create a temp invalid program (missing semicolon or unknown token) to trigger an error
        File tmp = File.createTempFile("dhr-json-test-", ".dhr");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write("class Main { static kaam main() { num x = ; } }");
        }
        String result = runProcess(JAVA, "-jar", jarName, "--json", tmp.getAbsolutePath());
        assertTrue(result.contains("\"diagnostics\""), "JSON output should contain diagnostics array. Got: " + result);
    }

    private String locateJar() {
        // Attempt to locate built jar (non-shadow) matching current version
        File libs = new File("build/libs");
        File[] jars = libs.listFiles((dir, name) -> name.matches("DhrLang-.*\\.jar") && !name.contains("sources") && !name.contains("javadoc"));
        assertNotNull(jars, "No jars found in build/libs – run gradle build before tests.");
        assertTrue(jars.length > 0, "Expected at least one jar artifact.");
        return jars[0].getPath();
    }
}
