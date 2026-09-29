package dhrlang;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

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
        String result = runPackagedJar("--help");
        assertTrue(result.startsWith("0\n"), result);
        assertTrue(result.contains("Usage: java -jar DhrLang.jar"), "Help output should contain usage line. Got: " + result);
    }

    @Test
    void versionPrintsSemanticVersion() throws Exception {
        String result = runPackagedJar("--version");
        assertTrue(result.startsWith("0\n"), result);
        assertTrue(result.matches("(?s).*DhrLang version .*"), "Version output missing. Got: " + result);
    }

    @Test
    void jsonModeOutputsJsonOnError() throws Exception {
        // Create a temp invalid program (missing semicolon or unknown token) to trigger an error
        File tmp = File.createTempFile("dhr-json-test-", ".dhr");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write("class Main { static kaam main() { num x = ; } }");
        }
        String result = runPackagedJar("--json", tmp.getAbsolutePath());
    // Current JSON structure uses top-level 'errors' and 'warnings' arrays
    assertTrue(result.contains("\"errors\"") && result.contains("\"warnings\""),
        "JSON output should contain 'errors' and 'warnings'. Got: " + result);
    }

    private String runPackagedJar(String... toolArgs) throws IOException, InterruptedException {
        String artifact = System.getProperty("dhrlang.test.jar");
        assertNotNull(artifact, "Gradle must supply the packaged compiler");
        assertTrue(new File(artifact).isFile(), "Missing compiler: " + artifact);
        String[] full = new String[3 + toolArgs.length];
        full[0] = JAVA;
        full[1] = "-jar";
        full[2] = artifact;
        System.arraycopy(toolArgs, 0, full, 3, toolArgs.length);
        return runProcess(full);
    }
}
