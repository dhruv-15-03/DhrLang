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
        String result = runWithJarOrClasspath("--help");
        assertTrue(result.contains("Usage: java -jar DhrLang.jar"), "Help output should contain usage line. Got: " + result);
    }

    @Test
    void versionPrintsSemanticVersion() throws Exception {
        String result = runWithJarOrClasspath("--version");
        assertTrue(result.matches("(?s).*DhrLang version .*"), "Version output missing. Got: " + result);
    }

    @Test
    void jsonModeOutputsJsonOnError() throws Exception {
        // Create a temp invalid program (missing semicolon or unknown token) to trigger an error
        File tmp = File.createTempFile("dhr-json-test-", ".dhr");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write("class Main { static kaam main() { num x = ; } }");
        }
        String result = runWithJarOrClasspath("--json", tmp.getAbsolutePath());
    // Current JSON structure uses top-level 'errors' and 'warnings' arrays
    assertTrue(result.contains("\"errors\"") && result.contains("\"warnings\""),
        "JSON output should contain 'errors' and 'warnings'. Got: " + result);
    }

    private String runWithJarOrClasspath(String... toolArgs) throws IOException, InterruptedException {
        // Prefer running the assembled jar if present, but fall back to classpath execution to keep tests
        // robust when run without a prior `gradle build`.
        File libs = new File("build/libs");
        File jar = null;
        if (libs.exists()) {
            File[] jars = libs.listFiles((dir, name) -> name.matches("DhrLang-.*\\.jar") && !name.contains("sources") && !name.contains("javadoc"));
            if (jars != null && jars.length > 0) {
                jar = jars[0];
            }
        }

        if (jar != null && jar.exists()) {
            String[] full = new String[3 + toolArgs.length];
            full[0] = JAVA;
            full[1] = "-jar";
            full[2] = jar.getPath();
            System.arraycopy(toolArgs, 0, full, 3, toolArgs.length);
            return runProcess(full);
        } else {
            String cp = System.getProperty("java.class.path");
            String[] full = new String[4 + toolArgs.length];
            full[0] = JAVA;
            full[1] = "-cp";
            full[2] = cp;
            full[3] = "dhrlang.Main";
            System.arraycopy(toolArgs, 0, full, 4, toolArgs.length);
            return runProcess(full);
        }
    }
}
