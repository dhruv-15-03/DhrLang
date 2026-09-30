package dhrlang.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import dhrlang.error.ErrorCode;
import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRunnerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    private Path jar() { return Path.of(System.getProperty("dhrlang.test.jar")); }

    private ProjectRunner.Manifest manifest(List<String> files, String input) {
        return new ProjectRunner.Manifest(1, System.getProperty("dhrlang.test.version"),
                HostExecution.PROFILE, files, input, null);
    }

    private Path writeManifest(ProjectRunner.Manifest manifest) throws IOException {
        Path path = directory.resolve("dhrlang.json");
        JSON.writeValue(path.toFile(), manifest);
        return path;
    }

    private void source(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    @Test
    void runsClassesAcrossExplicitFilesAndMatchesItsPublishedSchema() throws Exception {
        source("lib/Budget.dhr", "class Budget { static num add(num value) { return value + 2; } }");
        source("Main.dhr", "class Main { static kaam main() { printLine(Budget.add(toNum(readLine()))); } }");
        var manifest = manifest(List.of("lib\\Budget.dhr", "Main.dhr"), "5\n");
        var loaded = ProjectRunner.load(writeManifest(manifest));
        var result = ProjectRunner.execute(loaded, false, jar());
        assertEquals(HostExecution.Status.SUCCESS, result.execution().status(), result.execution().message());
        assertEquals("7", result.execution().stdout().trim());
        assertEquals(List.of("lib/Budget.dhr", "Main.dhr"), result.sources());
        assertEquals(SourceBundle.fingerprint(loaded.sources()), result.execution().sourceSha256());
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(JSON.readTree(Path.of("project.schema.json").toFile()));
        assertTrue(schema.validate(JSON.valueToTree(manifest)).isEmpty());
    }

    @Test
    void checksCompilationWithoutRunningMainOrStaticInitializers() throws Exception {
        source("Main.dhr", """
                class Main {
                    static num value = fail();
                    static num fail() { printLine("side effect"); return 1; }
                    static kaam main() { while (true) {} }
                }
                """);
        var result = ProjectRunner.execute(ProjectRunner.load(writeManifest(manifest(List.of("Main.dhr"), ""))), true, jar());
        assertEquals("check", result.operation());
        assertEquals(HostExecution.Status.SUCCESS, result.execution().status(), result.execution().message());
        assertEquals("", result.execution().stdout());
        assertTrue(result.execution().message().contains("no program code executed"));
    }

    @Test
    void resolvesInterfacesAndLaterClassesAcrossSourceUnits() throws Exception {
        source("HasValue.dhr", "interface HasValue { num value(); }");
        source("Value.dhr", "class Value implements HasValue { num value() { return 7; } }");
        source("Main.dhr", "class Main { static kaam main() { Value v = new Value(); printLine(v.value()); } }");
        var result = ProjectRunner.execute(ProjectRunner.load(writeManifest(
                manifest(List.of("Main.dhr", "Value.dhr", "HasValue.dhr"), ""))), false, jar());
        assertEquals(HostExecution.Status.SUCCESS, result.execution().status(),
                result.execution().message() + result.execution().diagnostics());
        assertEquals("7", result.execution().stdout().trim());
    }

    @Test
    void honorsManifestOrderForStaticInitialization() throws Exception {
        source("First.dhr", "class First { static num value = init(); static num init() { printLine(\"first\"); return 1; } }");
        source("Second.dhr", "class Second { static num value = init(); static num init() { printLine(\"second\"); return 2; } }");
        source("Main.dhr", "class Main { static kaam main() { printLine(\"main\"); } }");
        for (var order : List.of(List.of("First.dhr", "Second.dhr", "Main.dhr"),
                List.of("Second.dhr", "First.dhr", "Main.dhr"))) {
            var result = ProjectRunner.execute(ProjectRunner.load(writeManifest(manifest(order, ""))), false, jar());
            assertEquals(HostExecution.Status.SUCCESS, result.execution().status(), result.execution().message());
            String expected = order.get(0).startsWith("First") ? "first\nsecond\nmain" : "second\nfirst\nmain";
            assertEquals(expected, result.execution().stdout().replace("\r\n", "\n").trim());
        }
    }

    @Test
    void aLexicalErrorCannotConsumeTheNextSourceFile() throws Exception {
        source("Broken.dhr", "/* never closed");
        source("Main.dhr", "class Main { static kaam main() { printLine(\"must not run\"); } }");
        var result = ProjectRunner.execute(ProjectRunner.load(writeManifest(
                manifest(List.of("Broken.dhr", "Main.dhr"), ""))), false, jar());
        assertEquals(HostExecution.Status.COMPILE_ERROR, result.execution().status());
        assertEquals("", result.execution().stdout());
        assertTrue(result.execution().diagnostics().stream().anyMatch(d -> d.message().contains("[Broken.dhr]")));
    }

    @Test
    void duplicateClassesAndMultipleEntryPointsAreRejectedAcrossFiles() throws Exception {
        source("A.dhr", "class Main { static kaam main() {} }");
        for (String other : List.of("class Main { static kaam main() {} }", "class Other { static kaam main() {} }")) {
            source("B.dhr", other);
            var result = ProjectRunner.execute(ProjectRunner.load(writeManifest(
                    manifest(List.of("A.dhr", "B.dhr"), ""))), true, jar());
            assertEquals(HostExecution.Status.COMPILE_ERROR, result.execution().status());
            assertFalse(result.execution().diagnostics().isEmpty());
        }
    }

    @Test
    void equalLocationDiagnosticsInDifferentFilesAreNotDeduplicated() {
        var sources = List.of(
                new SourceBundle.Source("A.dhr", "class A { static kaam fail() { num x = \"bad\"; } }"),
                new SourceBundle.Source("B.dhr", "class B { static kaam fail() { num x = \"bad\"; } }"),
                new SourceBundle.Source("Main.dhr", "class Main { static kaam main() {} }"));
        ErrorReporter reporter = new ErrorReporter();
        var program = SourceBundle.parse(sources, reporter);
        new TypeChecker(reporter).check(program);
        assertEquals(2, reporter.getErrors().size(), reporter.toJson());
        assertEquals(List.of("A.dhr", "B.dhr"), reporter.getErrors().stream()
                .map(error -> error.getLocation().getFilename()).toList());
    }

    @Test
    void projectSourceNamedRequestStillCarriesItsFilename() throws Exception {
        source("request.dhr", "class Main { static kaam main() { num value = \"wrong\"; } }");
        var result = ProjectRunner.execute(ProjectRunner.load(writeManifest(
                manifest(List.of("request.dhr"), ""))), true, jar());
        assertEquals(HostExecution.Status.COMPILE_ERROR, result.execution().status());
        assertTrue(result.execution().diagnostics().stream().anyMatch(d -> d.message().startsWith("[request.dhr]")));
    }

    @Test
    void perFileSuppressionDoesNotLeakIntoAnotherFile() {
        ErrorReporter reporter = new ErrorReporter();
        reporter.registerSource("A.dhr", "// @suppress UNUSED_VARIABLE\nnum value = 1;");
        reporter.registerSource("B.dhr", "num ignored = 0;\nnum value = 1;");
        reporter.warning(new SourceLocation("A.dhr", 2, 1), "unused", "", ErrorCode.UNUSED_VARIABLE);
        reporter.warning(new SourceLocation("B.dhr", 2, 1), "unused", "", ErrorCode.UNUSED_VARIABLE);
        assertEquals(1, reporter.getWarningCount());
        assertEquals("B.dhr", reporter.getWarnings().get(0).getLocation().getFilename());
    }

    @Test
    void preservesFilenamesAndOffsetsOnOrdinaryAndSyntheticTokens() {
        Token original = new Token(TokenType.IDENTIFIER, "value", 3, 7, 20, 25);
        Token named = original.inFile("lib/Values.dhr");
        assertNull(original.getLocation().getFilename());
        assertEquals("lib/Values.dhr", named.getLocation().getFilename());
        assertEquals(20, named.getStartOffset());
        assertNotEquals(original, named);
        var source = new SourceBundle.Source("lib/Values.dhr",
                "class Values { num[] values; num[] fetch() { return values; } }");
        var program = SourceBundle.parse(List.of(source), new ErrorReporter());
        assertEquals("lib/Values.dhr", program.getClasses().get(0).getVariables().get(0).getSourceLocation().getFilename());
        assertEquals("lib/Values.dhr", program.getClasses().get(0).getFunctions().get(0).getSourceLocation().getFilename());
    }

    @Test
    void rejectsMissingFilesDuplicatesTraversalAbsolutePathsAndUnknownFields() throws Exception {
        source("Main.dhr", "class Main { static kaam main() {} }");
        Path missing = writeManifest(manifest(List.of("missing.dhr"), ""));
        assertThrows(IOException.class, () -> ProjectRunner.load(missing));
        for (List<String> files : List.of(List.of("../Main.dhr"),
                List.of("..\\Main.dhr"), List.of(directory.resolve("Main.dhr").toString()),
                List.of("C:\\outside.dhr"), List.of("Main.dhr", "./Main.dhr"))) {
            Path manifestFile = writeManifest(manifest(files, ""));
            assertThrows(IllegalArgumentException.class, () -> ProjectRunner.load(manifestFile), files.toString());
        }
        Path manifestFile = directory.resolve("dhrlang.json");
        for (String invalid : List.of("null", "{\"schemaVersion\":\"1\"}",
                JSON.writeValueAsString(manifest(List.of("Main.dhr"), "")).replace("\"schemaVersion\":1",
                        "\"schemaVersion\":1,\"imports\":[]"))) {
            Files.writeString(manifestFile, invalid);
            assertThrows(IOException.class, () -> ProjectRunner.load(manifestFile));
        }
    }

    @Test
    void refusesSourcesThatEscapeThroughSymlinks() throws Exception {
        Path projectDirectory = directory.resolve("project");
        Files.createDirectories(projectDirectory);
        Path outside = directory.resolve("Outside.dhr");
        Files.writeString(outside, "class Main { static kaam main() {} }");
        try {
            Files.createSymbolicLink(projectDirectory.resolve("Alias.dhr"), outside);
        } catch (UnsupportedOperationException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable on this filesystem: " + unavailable.getMessage());
        } catch (java.nio.file.FileSystemException failure) {
            String reason = String.valueOf(failure.getReason()).toLowerCase(java.util.Locale.ROOT);
            if (System.getProperty("os.name").startsWith("Windows") && reason.contains("privilege")) {
                Assumptions.assumeTrue(false, "Windows symlink privilege unavailable: " + failure.getMessage());
            }
            throw failure;
        }
        Path manifestFile = projectDirectory.resolve("dhrlang.json");
        JSON.writeValue(manifestFile.toFile(), manifest(List.of("Alias.dhr"), ""));
        var error = assertThrows(IllegalArgumentException.class, () -> ProjectRunner.load(manifestFile));
        assertTrue(error.getMessage().contains("symlink escapes"));
    }

    @Test
    void boundsTheNumberAndCombinedSizeOfSources() throws Exception {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= SourceBundle.MAX_FILES; i++) tooMany.add("File" + i + ".dhr");
        assertThrows(IllegalArgumentException.class, () -> manifest(tooMany, ""));
        source("Large.dhr", " ".repeat(SourceBundle.MAX_SOURCE_CHARS + 1));
        Path manifestFile = writeManifest(manifest(List.of("Large.dhr"), ""));
        assertThrows(IllegalArgumentException.class, () -> ProjectRunner.load(manifestFile));
        Files.write(directory.resolve("Large.dhr"), new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> ProjectRunner.load(manifestFile));
    }

    @Test
    void snapshotsSourceAndRejectsACompilerVersionMismatch() throws Exception {
        source("Main.dhr", "class Main { static kaam main() { printLine(1); } }");
        var loaded = ProjectRunner.load(writeManifest(manifest(List.of("Main.dhr"), "")));
        source("Main.dhr", "class Main { static kaam main() { printLine(2); } }");
        var result = ProjectRunner.execute(loaded, false, jar());
        assertEquals("1", result.execution().stdout().trim());
        var mismatch = new ProjectRunner.Manifest(1, "999.0.0", HostExecution.PROFILE,
                List.of("Main.dhr"), "", null);
        assertThrows(IllegalArgumentException.class, () -> ProjectRunner.execute(
                ProjectRunner.load(writeManifest(mismatch)), true, jar()));
    }

    @Test
    void sourceFingerprintIncludesPathsContentsAndOrder() {
        var a = new SourceBundle.Source("A.dhr", "class A {}");
        var b = new SourceBundle.Source("B.dhr", "class B {}");
        String fingerprint = SourceBundle.fingerprint(List.of(a, b));
        assertEquals("2b8725ef86b61f120ba3380e9389126c3ed90afa567c1cc33b5215560745c43f", fingerprint,
                "Independent UTF-8 / big-endian length-framing vector");
        assertEquals(fingerprint, SourceBundle.fingerprint(List.of(a, b)));
        assertNotEquals(fingerprint, SourceBundle.fingerprint(List.of(b, a)));
        assertNotEquals(fingerprint, SourceBundle.fingerprint(List.of(
                new SourceBundle.Source("Renamed.dhr", a.content()), b)));
        assertNotEquals(fingerprint, SourceBundle.fingerprint(List.of(
                new SourceBundle.Source(a.path(), a.content() + " "), b)));
    }

    @Test
    void cliProducesAJsonResultWithAMeaningfulExitCode() throws Exception {
        source("Main.dhr", "class Main { static kaam main() { printLine(42); } }");
        Path manifestFile = writeManifest(manifest(List.of("Main.dhr"), ""));
        Path output = directory.resolve("cli.json");
        Path error = directory.resolve("cli.err");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx128m", "-jar", jar().toString(), "project", "run", manifestFile.toString())
                .redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(error) + Files.readString(output));
            var result = JSON.readTree(Files.readString(output));
            assertEquals("SUCCESS", result.path("execution").path("status").asText());
            assertEquals("42", result.path("execution").path("stdout").asText().trim());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
