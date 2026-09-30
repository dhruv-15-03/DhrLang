package dhrlang.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import dhrlang.bytecode.BytecodeWriter;
import dhrlang.bytecode.ExecutionTrace;
import dhrlang.error.ErrorReporter;
import dhrlang.ir.AstToIrLowerer;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionTraceTest {
    private static final String SOURCE = """
            class Main {
                static num twice(num value) {
                    return value * 2;
                }
                static kaam main() {
                    num value = toNum(readLine());
                    printLine(Main.twice(value));
                }
            }
            """;

    private static Path jar() {
        return Path.of(System.getProperty("dhrlang.test.jar"));
    }

    private static HostExecution.Request request(String source) {
        return new HostExecution.Request(1, HostExecution.PROFILE, source, "5\n", null);
    }

    @Test
    void sourceProvenanceDoesNotChangeUnoptimizedBytecode() {
        var reporter = new ErrorReporter();
        var program = SourceBundle.parse(List.of(new SourceBundle.Source("example.dhr", SOURCE)), reporter);
        new TypeChecker(reporter).check(program);
        assertFalse(reporter.hasErrors());
        var ordinary = new AstToIrLowerer(reporter);
        var traced = new AstToIrLowerer(reporter, true);
        byte[] ordinaryCode = new BytecodeWriter().write(ordinary.lower(program));
        byte[] tracedCode = new BytecodeWriter().write(traced.lower(program));
        assertArrayEquals(ordinaryCode, tracedCode);
        assertTrue(ordinary.getInstructionLocations().isEmpty());
        assertFalse(traced.getInstructionLocations().isEmpty());
        assertTrue(traced.getInstructionLocations().values().stream()
                .allMatch(location -> "example.dhr".equals(location.getFilename())));
    }

    @Test
    void executionTraceIsDeterministicAndLinkedToActualSourceInstructions() throws Exception {
        var input = request(SOURCE);
        var first = HostExecution.executeTraced(input, jar());
        var second = HostExecution.executeTraced(input, jar());
        var normal = HostExecution.execute(input, jar());
        assertEquals(HostExecution.Status.SUCCESS, first.execution().status(), first.execution().message());
        assertEquals(HostExecution.Status.SUCCESS, normal.status());
        assertEquals(normal.stdout(), first.execution().stdout());
        assertEquals(normal.sourceSha256(), first.execution().sourceSha256());
        assertEquals(first.trace(), second.trace());
        assertFalse(first.trace().truncated());
        assertFalse(first.trace().steps().isEmpty());
        assertTrue(first.trace().steps().stream().anyMatch(step -> "Main.twice".equals(step.function())
                && step.depth() == 1 && step.line() == 3 && "MUL".equals(step.opcode())));
        assertTrue(first.trace().steps().stream().allMatch(step -> "request.dhr".equals(step.file())
                && step.line() > 0 && step.column() > 0));
        for (int i = 0; i < first.trace().steps().size(); i++) {
            assertEquals(i + 1, first.trace().steps().get(i).sequence());
        }
    }

    @Test
    void traceCapsItsPrefixWithoutDisablingTheExecutionBudget() throws Exception {
        var input = new HostExecution.Request(1, HostExecution.PROFILE,
                "class Main { static kaam main() { while (true) {} } }", "",
                new HostExecution.Limits(5000, 500, 128, 4096));
        var result = HostExecution.executeTraced(input, jar());
        assertEquals(HostExecution.Status.RUNTIME_ERROR, result.execution().status(), result.execution().message());
        assertTrue(result.execution().diagnostics().stream().anyMatch(d -> d.message().contains("max instruction steps")));
        assertEquals(ExecutionTrace.MAX_STEPS, result.trace().steps().size());
        assertTrue(result.trace().truncated());
    }

    @Test
    void traceRetainsFailureEvidenceButNeverCapturesProgramValues() throws Exception {
        var input = new HostExecution.Request(1, HostExecution.PROFILE, """
                class Main {
                    static kaam main() {
                        sab secret = readLine();
                        printLine(secret);
                        throw "sensitive-literal";
                    }
                }
                """, "sensitive-input\n", null);
        var result = HostExecution.executeTraced(input, jar());
        assertEquals(HostExecution.Status.RUNTIME_ERROR, result.execution().status());
        assertEquals("sensitive-input", result.execution().stdout().strip());
        assertTrue(result.trace().steps().stream().anyMatch(step -> "THROW".equals(step.opcode()) && step.line() == 5));
        String traceJson = new ObjectMapper().writeValueAsString(result.trace());
        assertFalse(traceJson.contains("sensitive-input"));
        assertFalse(traceJson.contains("sensitive-literal"));
        assertFalse(traceJson.contains("timestamp"));
    }

    @Test
    void compilationAndForcedTerminationNeverInventTraceEvidence() throws Exception {
        var invalid = HostExecution.executeTraced(request(
                "class Main { static kaam main() { num value = ; } }"), jar());
        assertEquals(HostExecution.Status.COMPILE_ERROR, invalid.execution().status());
        assertTrue(invalid.trace().steps().isEmpty());
        assertFalse(invalid.trace().truncated());
        var timed = HostExecution.executeTraced(new HostExecution.Request(1, HostExecution.PROFILE,
                SOURCE, "5\n", new HostExecution.Limits(1, 100000, 128, 4096)), jar());
        assertEquals(HostExecution.Status.TIME_LIMIT, timed.execution().status());
        assertTrue(timed.trace().steps().isEmpty());
    }
}
