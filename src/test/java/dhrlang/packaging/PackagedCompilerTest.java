package dhrlang.packaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PackagedCompilerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path directory;

    private Path copyCompiler() throws IOException {
        String configured = System.getProperty("dhrlang.test.jar");
        assertNotNull(configured, "Gradle must supply the exact compiler artifact; no classpath fallback");
        Path source = Path.of(configured);
        assertTrue(Files.isRegularFile(source), "Missing compiler artifact: " + source);
        Path isolated = directory.resolve("DhrLang.jar");
        Files.copy(source, isolated);
        return isolated;
    }

    @Test
    void standaloneJarHasTheExpectedManifestAndRuntimeDependencies() throws Exception {
        try (JarFile jar = new JarFile(copyCompiler().toFile())) {
            var attributes = jar.getManifest().getMainAttributes();
            assertEquals("dhrlang.Main", attributes.getValue("Main-Class"));
            assertEquals(System.getProperty("dhrlang.test.version"),
                    attributes.getValue("Implementation-Version"));
            assertNotNull(jar.getEntry("org/bouncycastle/crypto/Digest.class"),
                    "The published compiler must include its crypto dependency");
            assertNotNull(jar.getEntry("org/apache/commons/lang3/StringUtils.class"),
                    "The published compiler must include its runtime dependencies");
            assertNotNull(jar.getEntry("com/fasterxml/jackson/databind/ObjectMapper.class"),
                    "The host protocol must work without a separate JSON classpath");
            assertNotNull(jar.getEntry("dhrlang/learn/exercises.json"),
                    "Offline practice must not depend on a checkout or network download");
        }
    }

    @Test
    void cryptoWorksWithoutTheGradleOrTestClasspath() throws Exception {
        Path jar = copyCompiler();
        // A platform-only parent prevents the test runtime from supplying missing dependencies.
        try (URLClassLoader isolated = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> selector = isolated.loadClass("dhrlang.evm.FunctionSelector");
            byte[] hash = (byte[]) selector.getMethod("keccak256", byte[].class)
                    .invoke(null, (Object) new byte[0]);
            assertEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470",
                    HexFormat.of().formatHex(hash));

            Class<?> walletClass = isolated.loadClass("dhrlang.deploy.WalletManager");
            Object wallet = walletClass.getConstructor().newInstance();
            try {
                // Public secp256k1 test scalar 1; never a funded account or a user's key.
                walletClass.getMethod("setExplicitKey", String.class)
                        .invoke(wallet, "0".repeat(63) + "1");
                assertEquals("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf",
                        walletClass.getMethod("getAddress").invoke(wallet));
                byte[] first = (byte[]) walletClass.getMethod("signDigest", byte[].class)
                        .invoke(wallet, (Object) hash);
                byte[] second = (byte[]) walletClass.getMethod("signDigest", byte[].class)
                        .invoke(wallet, (Object) hash);
                assertEquals(65, first.length);
                assertArrayEquals(first, second, "Signing must remain deterministic");
            } finally {
                walletClass.getMethod("clear").invoke(wallet);
            }
        }
    }

    @Test
    void copiedJarRunsVersionAndAProgramOutsideTheCheckout() throws Exception {
        Path jar = copyCompiler();
        Result version = run(jar, "", "--version");
        assertEquals(0, version.exitCode(), version.error());
        assertTrue(version.output().contains("DhrLang version " + System.getProperty("dhrlang.test.version")),
                version.output());

        Path source = directory.resolve("Hello.dhr");
        Files.writeString(source, """
                class Main {
                    static kaam main() { printLine("packaged compiler works"); }
                }
                """);
        for (String backend : List.of("ast", "ir", "bytecode")) {
            Result result = run(jar, "", "--backend=" + backend, source.toString());
            assertEquals(0, result.exitCode(), backend + ": " + result.error());
            assertEquals("packaged compiler works", result.output().trim());
        }
    }

    @Test
    void copiedJarIncludesTheOfflineLearnerTools() throws Exception {
        Path jar = copyCompiler();
        Result doctor = run(jar, "", "doctor");
        assertEquals(0, doctor.exitCode(), doctor.output() + doctor.error());
        assertTrue(doctor.output().contains("Installation checks passed"));
        Result lessons = run(jar, "", "learn", "list");
        assertEquals(0, lessons.exitCode(), lessons.error());
        assertTrue(lessons.output().contains("01-input") && lessons.output().contains("10-approval"));
    }

    @Test
    void copiedJarAnswersFramedLanguageServerRequests() throws Exception {
        Path jar = copyCompiler();
        String input = frame("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """) + frame("""
                {"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{
                  "textDocument":{"uri":"file:///packaging.dhr"},"position":{"line":0,"character":0}}}
                """) + frame("""
                {"jsonrpc":"2.0","id":3,"method":"shutdown","params":{}}
                """) + frame("""
                {"jsonrpc":"2.0","method":"exit"}
                """);
        Result result = run(jar, input, "--lsp");
        assertEquals(0, result.exitCode(), result.error());
        List<JsonNode> responses = readFrames(result.output());
        assertEquals(3, responses.size(), result.output());
        assertTrue(responses.get(0).path("result").has("capabilities"));
        assertTrue(responses.get(1).path("result").toString().contains("\"label\""),
                "The packaged server must answer completion, not just start");
        assertEquals(3, responses.get(2).path("id").asInt());
    }

    private record Result(int exitCode, String output, String error) {}

    private Result run(Path jar, String input, String... arguments) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx128m", "-Dfile.encoding=UTF-8", "-jar", jar.toString()));
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile(directory, "stdout-", ".txt");
        Path error = Files.createTempFile(directory, "stderr-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectOutput(output.toFile())
                .redirectError(error.toFile());
        for (String variable : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            builder.environment().remove(variable);
        }
        Process process = builder.start();
        try {
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Packaged compiler timed out");
            return new Result(process.exitValue(), Files.readString(output), Files.readString(error));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static String frame(String json) {
        return "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + json;
    }

    private static List<JsonNode> readFrames(String output) throws Exception {
        var input = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        List<JsonNode> responses = new ArrayList<>();
        while (input.available() > 0) {
            StringBuilder header = new StringBuilder();
            while (!header.toString().endsWith("\r\n\r\n")) {
                int next = input.read();
                assertNotEquals(-1, next, "Incomplete LSP frame");
                header.append((char) next);
            }
            assertTrue(header.toString().startsWith("Content-Length: "), header.toString());
            int length = Integer.parseInt(header.substring(16).trim());
            byte[] body = input.readNBytes(length);
            assertEquals(length, body.length);
            responses.add(JSON.readTree(body));
        }
        return responses;
    }
}
