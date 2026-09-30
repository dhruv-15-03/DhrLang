package dhrlang.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import dhrlang.host.HostExecution.Limits;
import dhrlang.host.HostExecution.Request;
import dhrlang.host.HostExecution.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HostExecutionTest {
    @TempDir Path temporary;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Path jar() {
        String artifact = System.getProperty("dhrlang.test.jar");
        assertNotNull(artifact, "Tests require the exact staged compiler");
        return Path.of(artifact);
    }

    private static Request request(String body, String input, Limits limits) {
        return new Request(1, HostExecution.PROFILE,
                "class Main { static kaam main() { " + body + " } }", input, limits);
    }

    @Test
    void successfulExecutionUsesExplicitInputAndReturnsVersionAndSourceIdentity() throws Exception {
        var request = request("printLine(toNum(readLine()) + 2);", "5\n", null);
        var result = HostExecution.execute(request, jar());
        assertEquals(Status.SUCCESS, result.status(), result.message() + result.stderr());
        assertEquals("7", result.stdout().trim());
        assertEquals(64, result.sourceSha256().length());
        assertEquals(0, result.workerExitCode());
        assertTrue(result.elapsedMs() >= 0);
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        var requestSchema = factory.getSchema(JSON.readTree(Path.of("host-request.schema.json").toFile()));
        var responseSchema = factory.getSchema(JSON.readTree(Path.of("host-response.schema.json").toFile()));
        assertTrue(requestSchema.validate(JSON.valueToTree(request)).isEmpty());
        assertTrue(responseSchema.validate(JSON.valueToTree(result)).isEmpty());
        assertEquals(Status.SUCCESS, HostExecution.execute(request, jar()).status());
    }

    @Test
    void compilationAndRuntimeFailuresAreNotSuccessShaped() throws Exception {
        var compile = HostExecution.execute(request("num x = ;", "", null), jar());
        assertEquals(Status.COMPILE_ERROR, compile.status(), compile.stderr());
        assertFalse(compile.diagnostics().isEmpty());
        var runtime = HostExecution.execute(request("num[] x = [1]; printLine(x[2]);", "", null), jar());
        assertEquals(Status.RUNTIME_ERROR, runtime.status(), runtime.message() + runtime.stderr());
        assertTrue(runtime.diagnostics().stream().anyMatch(d -> "INDEX_ERROR".equals(d.code())));
    }

    @Test
    void instructionBudgetTerminatesAnInfiniteLoop() throws Exception {
        var result = HostExecution.execute(request("while (true) {}", "",
                new Limits(5000, 100, 128, 4096)), jar());
        assertEquals(Status.RUNTIME_ERROR, result.status(), result.message() + result.stderr());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("max instruction steps")));
    }

    @Test
    void outputBudgetTerminatesFloodingAndCapsCombinedCapture() throws Exception {
        var result = HostExecution.execute(request("while (true) { printLine(\"01234567890123456789\"); }", "",
                new Limits(5000, 5_000_000, 128, 512)), jar());
        assertEquals(Status.OUTPUT_LIMIT, result.status(), result.message() + result.stderr());
        assertTrue(result.stdout().getBytes(StandardCharsets.UTF_8).length
                + result.stderr().getBytes(StandardCharsets.UTF_8).length <= 512);
    }

    @Test
    void unicodeOutputCannotExpandPastTheByteBudgetDuringDecoding() throws Exception {
        var result = HostExecution.execute(request("sab text = readLine(); while (true) { print(text); }", "\u20ac\n",
                new Limits(5000, 5_000_000, 128, 257)), jar());
        assertEquals(Status.OUTPUT_LIMIT, result.status(), result.message() + result.stderr());
        assertTrue(result.stdout().getBytes(StandardCharsets.UTF_8).length <= 257);
    }

    @Test
    void onlyIntentionalWorkerTerminationToleratesAClosedOutputPipe() throws Exception {
        Process worker = org.mockito.Mockito.mock(Process.class);
        InputStream closedPipe = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("Stream closed"); }
        };
        assertThrows(IOException.class, () -> HostExecution.capture(closedPipe, worker,
                new AtomicInteger(), new AtomicBoolean(), new AtomicBoolean(false), 256));
        assertEquals("", HostExecution.capture(closedPipe, worker, new AtomicInteger(),
                new AtomicBoolean(), new AtomicBoolean(true), 256).text());
    }

    @Test
    void wallClockBudgetCoversWorkerStartupAndCompilation() throws Exception {
        var result = HostExecution.execute(request("while (true) {}", "",
                new Limits(1, 5_000_000, 128, 4096)), jar());
        assertEquals(Status.TIME_LIMIT, result.status(), result.message() + result.stderr());
    }

    @Test
    void nondeterministicNativesAreDeniedAtRuntime() throws Exception {
        for (String expression : List.of("clock()", "random()", "randomRange(0, 10)", "sleep(1)")) {
            var result = HostExecution.execute(request(expression + ";", "", null), jar());
            assertEquals(Status.RUNTIME_ERROR, result.status(), expression + result.message() + result.stderr());
            assertTrue(result.diagnostics().stream().anyMatch(d -> "ACCESS_ERROR".equals(d.code())), expression);
        }
    }

    @Test
    void contractModeIsNotAHostCapability() throws Exception {
        var result = HostExecution.execute(new Request(1, HostExecution.PROFILE,
                "@contract class C { @constructor kaam init() {} }", "", null), jar());
        assertEquals(Status.COMPILE_ERROR, result.status());
        assertTrue(result.message().contains("not EVM contracts"));
    }

    @Test
    void unusedInputDoesNotBlockOrFailAnOtherwiseSuccessfulProgram() throws Exception {
        var result = HostExecution.execute(request("printLine(1);", "x".repeat(65536), null), jar());
        assertEquals(Status.SUCCESS, result.status(), result.message() + result.stderr());
        assertEquals("1", result.stdout().trim());
    }

    @Test
    void workerEnvironmentDoesNotInheritCredentialsClasspathOrJvmOptions() {
        assertEquals(Map.of("SystemRoot", "C:\\Windows"), HostExecution.workerEnvironment(Map.of(
                "SystemRoot", "C:\\Windows", "PATH", "untrusted",
                "CLASSPATH", "injected", "JAVA_TOOL_OPTIONS", "-javaagent:injected.jar",
                "_JAVA_OPTIONS", "-Xmx8g", "JDK_JAVA_OPTIONS", "-Xmx8g",
                "GH_TOKEN", "example-not-a-token", "SAP_PASSWORD", "example-not-a-password",
                "HOME", "parent-home")));
        assertTrue(HostExecution.workerEnvironment(Map.of("PATH", "untrusted")).isEmpty());
    }

    @Test
    void concurrentRequestsDoNotShareInputOrStaticState() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            String source = """
                    class Main {
                        static num counter = 0;
                        static kaam main() { counter = counter + 1; printLine(counter); printLine(readLine()); }
                    }
                    """;
            var first = pool.submit(() -> HostExecution.execute(
                    new Request(1, HostExecution.PROFILE, source, "first\n", null), jar()));
            var second = pool.submit(() -> HostExecution.execute(
                    new Request(1, HostExecution.PROFILE, source, "second\n", null), jar()));
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertEquals(Status.SUCCESS, a.status(), a.message() + a.stderr());
            assertEquals(Status.SUCCESS, b.status(), b.message() + b.stderr());
            assertEquals("1\nfirst", a.stdout().replace("\r\n", "\n").trim());
            assertEquals("1\nsecond", b.stdout().replace("\r\n", "\n").trim());
            assertEquals(a.sourceSha256(), b.sourceSha256());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void malformedAndAmbiguousRequestsAreRejected() throws Exception {
        String valid = JSON.writeValueAsString(request("printLine(1);", "", null));
        for (String invalid : List.of(
                "null",
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":null"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":2"),
                valid.replace("\"profile\":\"jvm-bytecode-v1\"", "\"profile\":\"evm\""),
                valid.substring(0, valid.length() - 1) + ",\"credentials\":\"forbidden\"}",
                valid + "{}")) {
            Path input = temporary.resolve("invalid.json");
            Files.writeString(input, invalid);
            assertThrows(IOException.class, () -> HostExecution.readRequest(input), invalid);
        }
        Path oversized = temporary.resolve("oversized.json");
        Files.writeString(oversized, " ".repeat(HostExecution.MAX_REQUEST_BYTES + 1));
        assertThrows(IOException.class, () -> HostExecution.readRequest(oversized));
        assertThrows(IllegalArgumentException.class, () -> new Limits(0, 100, 128, 4096));
        assertThrows(IllegalArgumentException.class, () -> new Limits(5000, -1, 128, 4096));
        assertThrows(IllegalArgumentException.class, () -> new Limits(5000, 100, 1024, 4096));
    }

    @Test
    void cliReturnsOnlyOneJsonDocumentAndPreservesSpecialOutput() throws Exception {
        Path request = temporary.resolve("request.json");
        JSON.writeValue(request.toFile(), request("printLine(readLine());", "\"quoted\"\n", null));
        Path output = temporary.resolve("output.json");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx128m", "-jar", jar().toString(), "host", request.toString())
                .redirectOutput(output.toFile()).redirectError(temporary.resolve("stderr.txt").toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(output));
            JsonNode result = JSON.readTree(Files.readString(output));
            assertEquals("SUCCESS", result.path("status").asText());
            assertEquals("\"quoted\"", result.path("stdout").asText().trim());
            assertEquals(1, result.path("schemaVersion").asInt());
            assertEquals(System.getProperty("dhrlang.test.version"), result.path("compilerVersion").asText());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
