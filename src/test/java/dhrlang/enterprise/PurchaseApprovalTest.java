package dhrlang.enterprise;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static dhrlang.enterprise.PurchaseApproval.*;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseApprovalTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static Path jar() {
        return Path.of(System.getProperty("dhrlang.test.jar"));
    }

    @TestFactory
    Stream<DynamicTest> javaAndDhrlangMatchTheSameIndependentFixtures() throws IOException {
        var fixtures = EnterpriseCli.fixtures();
        assertEquals(15, fixtures.cases().size());
        var runner = new PurchaseDecisionRunner(jar(), PurchaseDecisionRunner.referenceSource());
        return fixtures.cases().stream().map(fixture -> DynamicTest.dynamicTest(fixture.id(), () -> {
            assertEquals(fixture.expected(), evaluate(fixture.snapshot(), fixture.actor()));
            var result = runner.evaluate(fixture.snapshot(), fixture.actor());
            assertTrue(result.successful(), JSON.writeValueAsString(result));
            assertEquals(fixture.expected(), result.decision(), JSON.writeValueAsString(result));
            assertEquals(System.getProperty("dhrlang.test.version"), result.execution().compilerVersion());
            assertEquals(64, result.execution().sourceSha256().length());
        }));
    }

    @Test
    void numericTokensAreNotCoercedTruncatedOrDefaulted() throws Exception {
        String valid = JSON.writeValueAsString(EnterpriseCli.fixtures());
        for (String invalid : List.of(
                valid.replace("\"amountMinor\":12500", "\"amountMinor\":12500.5"),
                valid.replace("\"amountMinor\":12500", "\"amountMinor\":12500.0"),
                valid.replace("\"amountMinor\":12500", "\"amountMinor\":\"12500\""),
                valid.replace("\"amountMinor\":12500,", ""),
                valid.replace("\"amountMinor\":12500", "\"amountMinor\":null"),
                valid.replace("\"amountMinor\":12500", "\"amountMinor\":9007199254740992"),
                valid.replace("\"mayApprove\":true", "\"mayApprove\":1"),
                valid.replace("\"state\":\"SUBMITTED\"", "\"state\":0"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":2"),
                valid + "{}", "null")) {
            assertThrows(IOException.class, () -> EnterpriseCli.readFixtures(
                    new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    void profileBoundsCoverInputsAndDerivedMoneyValues() {
        assertThrows(IllegalArgumentException.class, () -> new Request(
                "p", "alice", "supplier", "engineering", "INR", MAX_MINOR_UNITS + 1, true, State.SUBMITTED, 0));
        assertThrows(IllegalArgumentException.class, () -> new Budget("engineering", "INR", MAX_MINOR_UNITS + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Principal(
                "training", "bob", true, Set.of("engineering"), "INR", MAX_MINOR_UNITS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Decision(Outcome.APPROVED, Reason.WITHIN_POLICY, MAX_MINOR_UNITS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Decision(Outcome.APPROVED, Reason.WITHIN_POLICY, -1));
        assertThrows(IllegalArgumentException.class, () -> new Decision(Outcome.REJECTED, Reason.WITHIN_POLICY, 0));
        assertThrows(IllegalArgumentException.class, () -> new Budget("engineering", "inr", 100, 0));
    }

    @Test
    void policyOutputMustBeAnExactIntegerAndAConsistentDecision() throws Exception {
        var fixture = EnterpriseCli.fixtures().cases().get(0);
        for (String remaining : List.of("37500.5", "37500.0", "9007199254740992", "-1")) {
            String source = "class Main { static kaam main() { printLine(\"APPROVED\");"
                    + " printLine(\"WITHIN_POLICY\"); printLine(\"" + remaining + "\"); } }";
            var evaluation = new PurchaseDecisionRunner(jar(), source).evaluate(fixture.snapshot(), fixture.actor());
            assertFalse(evaluation.successful());
            assertNull(evaluation.decision());
            assertTrue(evaluation.error().contains("Invalid policy output"));
        }
    }

    @Test
    void exportedPolicyAlsoRejectsOutOfProfileAndFractionalRawInput() throws Exception {
        var fixture = EnterpriseCli.fixtures().cases().get(0);
        String input = PurchaseDecisionRunner.input(fixture.snapshot(), fixture.actor());
        for (String badAmount : List.of("9007199254740992", "12500.5")) {
            String invalid = badAmount + input.substring(input.indexOf('\n'));
            var request = new HostExecution.Request(1, HostExecution.PROFILE,
                    PurchaseDecisionRunner.referenceSource(), invalid, null);
            assertEquals(HostExecution.Status.RUNTIME_ERROR, HostExecution.execute(request, jar()).status());
        }
    }

    @Test
    void writingAReferenceNeverOverwritesLearnerWork() throws Exception {
        Path source = directory.resolve("policy.dhr");
        PurchaseDecisionRunner.writeReference(source);
        assertEquals(PurchaseDecisionRunner.referenceSource(), Files.readString(source));
        Files.writeString(source, "learner work");
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> PurchaseDecisionRunner.writeReference(source));
        assertEquals("learner work", Files.readString(source));
    }

    @Test
    void malformedCommandsAreRejectedBeforeExecution() {
        for (String[] arguments : List.of(
                new String[]{"enterprise"}, new String[]{"enterprise", "unknown"},
                new String[]{"enterprise", "cases", "extra"}, new String[]{"enterprise", "start"},
                new String[]{"enterprise", "demo", "extra"},
                new String[]{"enterprise", "verify", "--unknown"},
                new String[]{"enterprise", "verify", "--json", "--json"})) {
            assertEquals(64, EnterpriseCli.runCli(arguments));
        }
    }
}
