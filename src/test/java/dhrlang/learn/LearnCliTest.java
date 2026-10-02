package dhrlang.learn;

import com.fasterxml.jackson.databind.ObjectMapper;
import dhrlang.host.HostExecution;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LearnCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    private Path standalone;

    private Path jar() {
        String configured = System.getProperty("dhrlang.test.jar");
        assertNotNull(configured, "Tests must use the exact staged standalone compiler");
        return Path.of(configured);
    }

    private static final Map<String, String> SOLUTIONS = Map.of(
            "01-input", "class Main { static kaam main() { printLine(toNum(readLine()) + 2); } }",
            "02-bounds", """
                    class Main { static kaam main() {
                        num amount = toNum(readLine()); num budget = toNum(readLine());
                        if (amount > 0 && amount <= budget) { printLine("APPROVE"); }
                        else { printLine("REJECT"); }
                    } }
                    """,
            "03-boundary", """
                    class Main { static kaam main() {
                        num age = toNum(readLine());
                        if (age >= 18) { printLine("ELIGIBLE"); } else { printLine("INELIGIBLE"); }
                    } }
                    """,
            "04-arrays", """
                    class Main { static kaam main() {
                        num[] values = [10,20,30]; num index = toNum(readLine());
                        if (index >= 0 && index < arrayLength(values)) { printLine(values[index]); }
                        else { printLine("INVALID"); }
                    } }
                    """,
            "05-loop", """
                    class Main { static kaam main() {
                        num limit = toNum(readLine()); num total = 0;
                        for (num value = 1; value <= limit; value = value + 1) { total = total + value; }
                        printLine(total);
                    } }
                    """,
            "06-functions", """
                    class Main {
                        static num total(num amount, num fee) { return amount + fee; }
                        static kaam main() {
                            num amount = toNum(readLine()); num fee = toNum(readLine());
                            printLine(Main.total(amount, fee));
                        }
                    }
                    """,
            "07-strings", "class Main { static kaam main() { printLine(readLine().trim().toUpperCase()); } }",
            "08-errors", """
                    class Main { static kaam main() {
                        try { num value = toNum(readLine()); printLine(value); }
                        catch (e) { printLine("INVALID"); }
                    } }
                    """,
            "09-state", """
                    class Counter {
                        num balance;
                        kaam init(num initial) { this.balance = initial; }
                        kaam deposit(num amount) { this.balance = this.balance + amount; }
                        num value() { return this.balance; }
                    }
                    class Main { static kaam main() {
                        num initial = toNum(readLine()); num amount = toNum(readLine());
                        Counter counter = new Counter(initial); counter.deposit(amount);
                        printLine(counter.value());
                    } }
                    """,
            "10-approval", """
                    class Main { static kaam main() {
                        num amount = toNum(readLine()); num budget = toNum(readLine());
                        sab requester = readLine(); sab approver = readLine();
                        if (amount > 0 && amount <= budget && requester != approver) { printLine("APPROVE"); }
                        else { printLine("REJECT"); }
                    } }
                    """
    );

    @TestFactory
    Stream<DynamicTest> everyStarterFailsAndEveryReferenceSolutionPasses() throws IOException {
        var catalog = LearnCli.catalog();
        assertEquals(10, catalog.exercises().size());
        return catalog.exercises().stream().map(exercise -> DynamicTest.dynamicTest(
                exercise.id(), () -> {
                    var starter = LearnCli.check(exercise, exercise.starter(), jar());
                    assertTrue(starter.passed() < starter.total(), "Starter unexpectedly passes: " + exercise.id());
                    assertTrue(starter.cases().stream().noneMatch(sample ->
                                    sample.status() == HostExecution.Status.COMPILE_ERROR
                                    || sample.status() == HostExecution.Status.WORKER_ERROR
                                    || sample.status() == HostExecution.Status.INVALID_REQUEST),
                            "Starters must compile and reach their intended teaching bug: " + JSON.writeValueAsString(starter));
                    String solution = SOLUTIONS.get(exercise.id());
                    assertNotNull(solution);
                    var completed = LearnCli.check(exercise, solution, jar());
                    assertEquals(3, completed.total());
                    assertEquals(completed.total(), completed.passed(), JSON.writeValueAsString(completed));
                    assertEquals(System.getProperty("dhrlang.test.version"), completed.compilerVersion());
                    assertEquals(64, completed.sourceSha256().length());
                    assertEquals(3, exercise.hints().size());
                    assertFalse(exercise.transfer().isBlank());
                }));
    }

    @Test
    void startsWithoutOverwritingLearnerWork() throws Exception {
        Path destination = directory.resolve("answer.dhr");
        var exercise = LearnCli.exercise("01-input");
        LearnCli.start(exercise, destination);
        assertEquals(exercise.starter(), Files.readString(destination));
        Files.writeString(destination, "my existing work");
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> LearnCli.start(exercise, destination));
        assertEquals("my existing work", Files.readString(destination));
    }

    @Test
    void catalogRejectsAmbiguousMalformedOrUnsupportedMetadata() throws Exception {
        String valid = JSON.writeValueAsString(LearnCli.catalog());
        for (String invalid : List.of(
                "null", valid + "{}",
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":2"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":null"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"schemaVersion\":1", "\"unknown\":true,\"schemaVersion\":1"),
                valid.replace("\"02-bounds\"", "\"01-input\""),
                "{\"schemaVersion\":1,\"exercises\":[null]}",
                "{\"schemaVersion\":1,\"exercises\":[]}")) {
            assertThrows(IOException.class, () -> LearnCli.readCatalog(
                    new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
        }
        assertThrows(IOException.class, () -> LearnCli.readCatalog(
                new ByteArrayInputStream(new byte[128 * 1024 + 1])));
    }

    @Test
    void exerciseMetadataCannotChangeAfterValidation() throws Exception {
        var exercise = LearnCli.exercise("01-input");
        var hints = new ArrayList<>(exercise.hints());
        var cases = new ArrayList<>(exercise.cases());
        var copy = new LearnCli.Exercise(exercise.id(), exercise.title(), exercise.goal(),
                exercise.starter(), hints, cases, exercise.transfer());
        hints.clear();
        cases.clear();
        assertEquals(3, copy.hints().size());
        assertEquals(3, copy.cases().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.cases().clear());
        assertThrows(IllegalArgumentException.class, () -> LearnCli.exercise("unknown"));
    }

    @Test
    void outputNormalizationDoesNotHideWhitespaceOrExtraLines() {
        assertEquals(" HELLO ", LearnCli.normalizeOutput(" HELLO \r\n"));
        assertEquals("first\nsecond", LearnCli.normalizeOutput("first\r\nsecond\r\n"));
        assertNotEquals("HELLO", LearnCli.normalizeOutput("HELLO\n\n"));
        assertEquals("", LearnCli.normalizeOutput("\n"));
    }

    @Test
    void printingExpectedOutputBeforeAFailureDoesNotPass() throws Exception {
        var exercise = new LearnCli.Exercise("99-failure", "Runtime failure", "Reject failed executions", "starter",
                List.of("a", "b", "c"), List.of(new LearnCli.Case("failure", "", "7")), "explain");
        var result = LearnCli.check(exercise,
                "class Main { static kaam main() { printLine(7); throw \"not successful\"; } }", jar());
        assertEquals(0, result.passed());
        assertEquals("7", result.cases().get(0).actual());
        assertEquals(HostExecution.Status.RUNTIME_ERROR, result.cases().get(0).status());
        assertEquals(1, LearnCli.exitCode(result));
    }

    @Test
    void compilationErrorsKeepTheirDiagnosticEvidence() throws Exception {
        var result = LearnCli.check(LearnCli.exercise("01-input"),
                "class Main { static kaam main() { num value = ; } }", jar());
        assertEquals(0, result.passed());
        assertEquals(1, LearnCli.exitCode(result));
        assertTrue(result.cases().stream().allMatch(sample -> sample.status() == HostExecution.Status.COMPILE_ERROR
                && !sample.diagnostics().isEmpty() && sample.diagnostics().get(0).line() > 0));
    }

    private static LearnCli.Result recorded(HostExecution.Status status) {
        boolean passed = status == HostExecution.Status.SUCCESS;
        var sample = new LearnCli.CaseResult("case", "5\n", passed, status, "7", "7", "", List.of(), "test evidence");
        return new LearnCli.Result(1, "4.0.2", HostExecution.PROFILE, "01-input", "0".repeat(64),
                passed ? 1 : 0, 1, List.of(sample), "Port the calculation");
    }

    @Test
    void infrastructureFailureHasADistinctExitCode() {
        assertEquals(0, LearnCli.exitCode(recorded(HostExecution.Status.SUCCESS)));
        assertEquals(1, LearnCli.exitCode(recorded(HostExecution.Status.RUNTIME_ERROR)));
        assertEquals(1, LearnCli.exitCode(recorded(HostExecution.Status.TIME_LIMIT)));
        assertEquals(1, LearnCli.exitCode(recorded(HostExecution.Status.OUTPUT_LIMIT)));
        assertEquals(2, LearnCli.exitCode(recorded(HostExecution.Status.WORKER_ERROR)));
        assertEquals(2, LearnCli.exitCode(recorded(HostExecution.Status.INVALID_REQUEST)));
    }

    @Test
    void savedReportsRoundTripWithoutOverwritingExistingWork() throws Exception {
        Path report = directory.resolve("attempt.json");
        var result = recorded(HostExecution.Status.SUCCESS);
        LearnCli.save(result, report);
        assertEquals(result, LearnCli.readResult(report));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> LearnCli.save(recorded(HostExecution.Status.RUNTIME_ERROR), report));
        assertEquals(result, LearnCli.readResult(report));
        Files.writeString(report, JSON.writeValueAsString(result).replace("\"passed\":1", "\"passed\":0"));
        assertThrows(IOException.class, () -> LearnCli.readResult(report));
        Files.writeString(report, "null");
        assertThrows(IOException.class, () -> LearnCli.readResult(report));
        Files.writeString(report, JSON.writeValueAsString(result)
                .replace("01-input", "01-" + "a-".repeat(20000) + "a"));
        assertThrows(IOException.class, () -> LearnCli.readResult(report));
    }

    @Test
    void inconsistentPassFlagsCannotBecomeSuccessfulEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new LearnCli.CaseResult(
                "case", "", true, HostExecution.Status.RUNTIME_ERROR, "7", "7", "", List.of(), "failed"));
        assertThrows(IllegalArgumentException.class, () -> new LearnCli.CaseResult(
                "case", "", true, HostExecution.Status.SUCCESS, "7", "7", "warning", List.of(), "stderr"));
        assertThrows(IllegalArgumentException.class, () -> new LearnCli.CaseResult(
                "case", "", true, HostExecution.Status.SUCCESS, "7", "8", "", List.of(), "mismatch"));
    }

    @Test
    void invalidCliArgumentsAreRejectedBeforeExecution() {
        for (String[] args : List.of(
                new String[]{"learn"}, new String[]{"learn", "list", "extra"},
                new String[]{"learn", "show"}, new String[]{"learn", "hint", "01-input", "invalid"},
                new String[]{"learn", "hint", "01-input", "0"}, new String[]{"learn", "hint", "01-input", "4"},
                new String[]{"learn", "unknown"}, new String[]{"learn", "progress"},
                new String[]{"learn", "trace", "01-input", "unused.dhr", "0"},
                new String[]{"learn", "trace", "01-input", "unused.dhr", "4"},
                new String[]{"learn", "check", "01-input", "unused.dhr", "--json", "--json"},
                new String[]{"learn", "check", "01-input", "unused.dhr", "--record"},
                new String[]{"learn", "check", "01-input", "unused.dhr", "--record", "--json"})) {
            assertEquals(64, LearnCli.runCli(args), String.join(" ", args));
        }
        assertEquals(64, DoctorCli.runCli(new String[]{"doctor", "extra"}));
    }

    @Test
    void doctorChecksTheActualSelfContainedJar() throws Exception {
        var result = DoctorCli.inspect(jar());
        assertTrue(result.ready(), result.checks().toString());
        assertEquals(System.getProperty("dhrlang.test.version"), result.compilerVersion());
        Path thin = Path.of("build", "libs", "plain", "DhrLang-" + System.getProperty("dhrlang.test.version") + ".jar");
        assertFalse(DoctorCli.inspect(thin).ready(), "A thin JAR is not a self-contained installation");
    }

    @Test
    void doctorDoesNotAcceptAWrongEntryPointOrMissingComponents() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "wrong.Main");
        manifest.getMainAttributes().putValue("Implementation-Version", "4.0.2");
        Path incomplete = directory.resolve("incomplete.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(incomplete), manifest)) {
            output.flush();
        }
        var report = DoctorCli.inspect(incomplete);
        assertFalse(report.ready());
        assertTrue(report.checks().stream().anyMatch(check -> "manifest".equals(check.id()) && !check.passed()
                && check.detail().contains("wrong.Main")));
        assertTrue(report.checks().stream().anyMatch(check -> "dhrlang/learn/exercises.json".equals(check.id()) && !check.passed()));
    }

    @Test
    void copiedCompilerProvidesOfflineGuidanceAndNonDestructiveStart() throws Exception {
        assertEquals(0, run("doctor").exit());
        var list = run("learn", "list");
        assertEquals(0, list.exit(), list.error());
        assertTrue(list.output().contains("01-input") && list.output().contains("10-approval"));
        var show = run("learn", "show", "01-input");
        assertEquals(0, show.exit(), show.error());
        assertTrue(show.output().contains("Practice cases") && show.output().contains("Transfer task"));
        assertEquals(LearnCli.exercise("01-input").hints().get(0), run("learn", "hint", "01-input").output().strip());
        assertEquals(LearnCli.exercise("01-input").hints().get(2), run("learn", "hint", "01-input", "3").output().strip());
        Path answer = directory.resolve("answer.dhr");
        assertEquals(0, run("learn", "start", "01-input", answer.toString()).exit());
        assertEquals(2, run("learn", "start", "01-input", answer.toString()).exit());
        assertEquals(LearnCli.exercise("01-input").starter(), Files.readString(answer));
    }

    @Test
    void copiedCompilerRecordsMachineReadableGradingAndDisplaysProgress() throws Exception {
        Path submission = directory.resolve("answer.dhr");
        Files.writeString(submission, SOLUTIONS.get("01-input"));
        Path saved = directory.resolve("result.json");
        var execution = run("learn", "check", "01-input", submission.toString(), "--record", saved.toString(), "--json");
        assertEquals(0, execution.exit(), execution.error() + execution.output());
        var result = JSON.readTree(execution.output());
        assertEquals(3, result.path("passed").asInt());
        assertEquals(3, result.path("total").asInt());
        assertEquals(System.getProperty("dhrlang.test.version"), result.path("compilerVersion").asText());
        assertEquals(result, JSON.readTree(Files.readString(saved)));
        assertFalse(result.has("source"));
        var progress = run("learn", "progress", saved.toString());
        assertEquals(0, progress.exit(), progress.error());
        assertTrue(progress.output().contains("01-input: 3/3"));
        assertTrue(progress.output().contains("not revalidated"));
        assertEquals(SOLUTIONS.get("01-input"), Files.readString(submission));
    }

    @Test
    void copiedCompilerTracesOneCaseWithoutPretendingToGradeIt() throws Exception {
        Path submission = directory.resolve("answer.dhr");
        Files.writeString(submission, LearnCli.exercise("01-input").starter());
        var execution = run("learn", "trace", "01-input", submission.toString(), "1", "--json");
        assertEquals(0, execution.exit(), execution.error() + execution.output());
        var result = JSON.readTree(execution.output());
        assertEquals("unoptimized-bytecode-trace", result.path("mode").asText());
        assertEquals("positive input", result.path("caseName").asText());
        assertFalse(result.has("passed"), "Trace success is not a grading pass");
        assertTrue(result.path("result").path("trace").path("steps").size() > 0);
        assertEquals("3", result.path("result").path("execution").path("stdout").asText().strip());
        assertEquals("7", result.path("expected").asText());
    }

    @Test
    void copiedCompilerRejectsInvalidSubmissionAndReportFiles() throws Exception {
        Path invalid = directory.resolve("invalid.dhr");
        Files.write(invalid, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertEquals(2, run("learn", "check", "01-input", invalid.toString(), "--json").exit());
        assertEquals(2, run("learn", "progress", directory.resolve("absent.json").toString()).exit());
        Files.writeString(invalid, " ".repeat(HostExecution.MAX_REQUEST_BYTES + 1));
        assertEquals(64, run("learn", "check", "01-input", invalid.toString()).exit());
    }

    private record CliResult(int exit, String output, String error) {}

    private CliResult run(String... arguments) throws Exception {
        if (standalone == null) {
            standalone = directory.resolve("DhrLang.jar");
            Files.copy(jar(), standalone);
        }
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xms16m", "-Xmx128m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8", "-jar", standalone.toString()));
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile(directory, "stdout-", ".txt");
        Path error = Files.createTempFile(directory, "stderr-", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(output.toFile()).redirectError(error.toFile());
        for (String variable : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            builder.environment().remove(variable);
        }
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Packaged learner command timed out");
            return new CliResult(process.exitValue(), Files.readString(output), Files.readString(error));
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }
}
