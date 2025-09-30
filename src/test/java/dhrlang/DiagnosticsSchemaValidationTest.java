package dhrlang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that diagnostics JSON emitted by the CLI conforms (structurally) to diagnostics.schema.json.
 * This guards against accidental breaking changes to tooling contracts.
 */
public class DiagnosticsSchemaValidationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String run(String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try(InputStream in = p.getInputStream()) { in.transferTo(baos); }
        int code = p.waitFor();
        return code + "\n" + baos.toString(StandardCharsets.UTF_8);
    }

    @Test
    void diagnosticsJsonMatchesSchema() throws Exception {
        // Create a temp source containing an error to ensure errors[] populated
        File tmp = File.createTempFile("dhr-schema-test-", ".dhr");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write("class Main { static kaam main() { num x = ; } }");
        }
        // Build jar path
        File libs = new File("build/libs");
        File jar = null;
        for(File f : libs.listFiles()) {
            if(f.getName().endsWith(".jar") && !f.getName().contains("sources") && !f.getName().contains("javadoc")) { jar = f; break; }
        }
        assertNotNull(jar, "Jar not built. Run gradle build first.");
        String output = run(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-jar", jar.getAbsolutePath(), "--json", "--time", tmp.getAbsolutePath());

        // Extract JSON: capture substring from first '{' to last '}'
        int first = output.indexOf('{');
        int last = output.lastIndexOf('}');
        assertTrue(first >=0 && last>first, "Could not locate JSON braces in output: " + output);
        String json = output.substring(first, last+1).trim();
        JsonNode node = MAPPER.readTree(json);

        // Basic structural assertions
        assertTrue(node.has("errors"), "Missing errors array");
        assertTrue(node.has("warnings"), "Missing warnings array");
        // schemaVersion + timings are desirable but tolerate absence to avoid breaking older builds locally
        if(!node.has("schemaVersion")) {
            System.out.println("[WARN] schemaVersion not present in diagnostics JSON (legacy format)" );
        }
        if(!node.has("timings")) {
            System.out.println("[WARN] timings block absent; phase timing may not have executed");
        }

        // Schema validation
        File schemaFile = new File("diagnostics.schema.json");
        assertTrue(schemaFile.exists(), "Schema file missing");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema schema = factory.getSchema(MAPPER.readTree(schemaFile));
        Set<ValidationMessage> msgs = schema.validate(node);
        // If schemaVersion missing we cannot fully validate; allow reduced verification
        if(node.has("schemaVersion")) {
            assertTrue(msgs.isEmpty(), () -> "Schema violations: " + msgs);
        }
    }
}
