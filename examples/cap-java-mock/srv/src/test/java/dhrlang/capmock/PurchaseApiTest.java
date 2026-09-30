package dhrlang.capmock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dhrlang.enterprise.PurchaseApproval;
import dhrlang.enterprise.PurchaseDecisionRunner;
import dhrlang.enterprise.PurchaseWorkflow;
import dhrlang.host.HostExecution;
import dhrlang.host.SourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {LocalCapMockApplication.class, PurchaseApiTest.Harness.class},
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@AutoConfigureMockMvc
@ActiveProfiles("local-cap-mock")
class PurchaseApiTest {
    private static final String ROOT = "/odata/v4/purchase-lab";
    private static final String PASSWORD = "local-test-only";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired TenantWorkflows workflows;
    @Autowired ProbingEngine probe;
    @Autowired Path compilerJar;

    @BeforeEach
    void resetFixtures() {
        workflows.reset();
        probe.reset();
    }

    @Test
    void actualCapOdataMetadataAndAuthenticationAreActive() throws Exception {
        mvc.perform(get(ROOT + "/$metadata")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/$metadata").with(httpBasic("bob-a", PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Action Name=\"approve\"")))
                .andExpect(content().string(containsString("Edm.Int64")));
        mvc.perform(get(ROOT + "/$metadata").with(httpBasic("bob-a", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/$metadata").with(httpBasic("privileged", "")))
                .andExpect(status().isUnauthorized());
        assertEquals(0, probe.calls.get());
    }

    @Test
    void actualFrameworkAndHostDenyUntrustedIdentityAndRoleClaims() throws Exception {
        mvc.perform(post(ROOT + "/approve").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(command("no-auth", "purchase-001", "0", "0"))).andExpect(status().isUnauthorized());
        for (String user : List.of("viewer-a", "alice-a", "sales-a", "unmapped-a", "foreign")) {
            submit(user, command("denied", "purchase-001", "0", "0")).andExpect(status().isForbidden());
        }
        String forged = """
                {"command":{"eventId":"forged","requestId":"purchase-001","requestVersion":"0","budgetVersion":"0",
                  "actor":"bob-a","tenant":"training-a","roles":["Approver"],"mayApprove":true,
                  "source":"class Main { static kaam main() {} }"}}
                """;
        submit("viewer-a", forged).andExpect(status().is4xxClientError());
        assertEquals(0, probe.calls.get());
        assertTrue(workflows.store("training-a").audit().isEmpty());
        assertEquals(50000, balance("training-a"));
    }

    @Test
    void approvalAndReplayUseAuthenticatedContextAndTheRealBoundedRunner() throws Exception {
        String body = command("approve", "purchase-001", "0", "0");
        var first = response(submit("bob-a", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value(LocalCapMockApplication.PROFILE))
                .andExpect(jsonPath("$.status").value("APPLIED")));
        assertEquals("bob-a", first.path("receipt").path("actor").asText());
        assertEquals("training-a", first.path("receipt").path("tenant").asText());
        assertEquals(37500, first.path("receipt").path("budgetMinor").asLong());
        assertEquals(64, first.path("receipt").path("sourceSha256").asText().length());
        var replay = response(submit("bob-a", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPLAYED")));
        assertEquals(first.path("receipt"), replay.path("receipt"));
        assertEquals(1, probe.calls.get());
        assertEquals(1, workflows.store("training-a").audit().size());
    }

    @Test
    void mockTenantsHaveSeparateAuthoritativeStateAndHeaderSpoofingCannotCrossIt() throws Exception {
        String body = command("same-event", "purchase-001", "0", "0");
        submit("bob-a", body).andExpect(status().isOk()).andExpect(jsonPath("$.receipt.tenant").value("training-a"));
        var second = response(submit("bob-b", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.receipt.tenant").value("training-b")));
        assertEquals(67500, second.path("receipt").path("budgetMinor").asLong());
        assertEquals(37500, balance("training-a"));
        assertEquals(67500, balance("training-b"));
        var spoof = mvc.perform(get(ROOT + "/snapshot(requestId='purchase-001')")
                .with(httpBasic("bob-a", PASSWORD)).header("X-Tenant-ID", "training-b")
                .header("X-Identity-Zone-Id", "training-b").header("X-SAP-Tenant", "training-b")).andReturn();
        if (spoof.getResponse().getStatus() == 200) {
            assertEquals("training-a", JSON.readTree(spoof.getResponse().getContentAsString()).path("tenant").asText());
        } else {
            assertEquals(403, spoof.getResponse().getStatus());
        }
    }

    @Test
    void fractionalMissingBooleanAndOutOfRangeRevisionsAreRejectedByTheApi() throws Exception {
        String valid = command("bad-input", "purchase-001", "0", "0");
        for (String token : List.of("0", "0.5", "0.0", "-1", "null", "true", "\"0.5\"", "\"0.0\"",
                "\"01\"", "\"+0\"", "\" 0\"", "\"9223372036854775808\"")) {
            submit("bob-a", valid.replace("\"requestVersion\":\"0\"", "\"requestVersion\":" + token))
                    .andExpect(status().isBadRequest());
        }
        submit("bob-a", valid.replace("\"requestVersion\":\"0\",", "")).andExpect(status().isBadRequest());
        submit("bob-a", "{}").andExpect(status().isBadRequest());
        assertEquals(0, probe.calls.get());
        assertTrue(workflows.store("training-a").audit().isEmpty());
    }

    @Test
    void revisionStringsRoundTripFromSnapshotThroughReviewReceiptAndNextCommand() throws Exception {
        long exact = TenantWorkflows.PRECISE_VERSION;
        JsonNode initial = response(mvc.perform(get(ROOT + "/snapshot(requestId='precise-version')")
                .with(httpBasic("bob-a", PASSWORD)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()));
        assertTrue(initial.path("requestVersion").isTextual());
        assertTrue(initial.path("budgetVersion").isTextual());
        assertEquals("9007199254740993", initial.path("requestVersion").textValue());
        assertEquals("9007199254740993", initial.path("budgetVersion").textValue());
        submit("bob-a", command("wrong-revision", "precise-version", Long.toString(exact - 1), Long.toString(exact - 1)))
                .andExpect(status().isConflict());
        JsonNode reviewed = response(submit("reviewer-a", command("exact-review", "precise-version",
                initial.path("requestVersion").textValue(), initial.path("budgetVersion").textValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt.outcome").value("NEEDS_REVIEW"))).path("receipt");
        assertTrue(reviewed.path("requestVersion").isTextual());
        assertTrue(reviewed.path("budgetVersion").isTextual());
        assertEquals("9007199254740994", reviewed.path("requestVersion").textValue());
        assertEquals("9007199254740993", reviewed.path("budgetVersion").textValue());
        JsonNode approved = response(submit("bob-a", command("exact-approval", "precise-version",
                reviewed.path("requestVersion").textValue(), reviewed.path("budgetVersion").textValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt.outcome").value("APPROVED"))).path("receipt");
        assertTrue(approved.path("requestVersion").isTextual());
        assertTrue(approved.path("budgetVersion").isTextual());
        assertEquals("9007199254740995", approved.path("requestVersion").textValue());
        assertEquals("9007199254740994", approved.path("budgetVersion").textValue());
        assertEquals(37500, approved.path("budgetMinor").asLong());
        assertEquals(2, probe.calls.get());
    }

    @Test
    void businessRejectionAndReviewPreserveTheExistingHostRules() throws Exception {
        submit("bob-a", command("invalid-amount", "invalid-amount", "0", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.receipt.reason").value("INVALID_AMOUNT"));
        assertEquals(50000, balance("training-a"));
        submit("reviewer-a", command("review", "purchase-001", "0", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt.outcome").value("NEEDS_REVIEW"));
        assertEquals(50000, balance("training-a"));
        submit("bob-a", command("approved-review", "purchase-001", "1", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt.outcome").value("APPROVED"));
        assertEquals(37500, balance("training-a"));
    }

    @Test
    void staleVersionsAndDifferentActorOrTermsCannotReuseAnEvent() throws Exception {
        String body = command("bound-event", "purchase-001", "0", "0");
        submit("bob-a", body).andExpect(status().isOk());
        submit("reviewer-a", body).andExpect(status().isConflict());
        submit("bob-a", command("bound-event", "purchase-001", "1", "1")).andExpect(status().isConflict());
        submit("bob-a", command("new-stale-event", "purchase-001", "0", "0")).andExpect(status().isConflict());
        assertEquals(1, probe.calls.get());
        assertEquals(1, workflows.store("training-a").audit().size());
    }

    @Test
    void preCommitFailureIsAnErrorAndRetryDoesNotInventAnEarlierCommit() throws Exception {
        String body = command("retry", "purchase-001", "0", "0");
        workflows.store("training-a").failNextCommit(PurchaseWorkflow.Fault.BEFORE_COMMIT);
        submit("bob-a", body).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("STORE_RETRYABLE"));
        assertTrue(workflows.store("training-a").audit().isEmpty());
        assertEquals(50000, balance("training-a"));
        submit("bob-a", body).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
        assertEquals(1, workflows.store("training-a").audit().size());
    }

    @Test
    void capErrorHandlingDoesNotPretendToRollBackAnUncertainMockCommit() throws Exception {
        String body = command("lost-ack", "purchase-001", "0", "0");
        workflows.store("training-a").failNextCommit(PurchaseWorkflow.Fault.AFTER_COMMIT);
        submit("bob-a", body).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("RECONCILIATION_REQUIRED"));
        assertEquals(37500, balance("training-a"));
        assertEquals(1, workflows.store("training-a").audit().size());
        submit("reviewer-a", body).andExpect(status().isConflict());
        submit("viewer-a", body).andExpect(status().isForbidden());
        submit("bob-a", body).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REPLAYED"));
        assertEquals(1, probe.calls.get());
        assertEquals(37500, balance("training-a"));
    }

    @Test
    void anIncorrectActualDhrlangProposalCannotOverrideHostPolicy() throws Exception {
        probe.delegate = new PurchaseDecisionRunner(compilerJar, """
                class Main { static kaam main() {
                    printLine("APPROVED"); printLine("WITHIN_POLICY"); printLine(0);
                } }
                """);
        submit("bob-a", command("malicious-proposal", "purchase-001", "0", "0"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("POLICY_MISMATCH"));
        assertEquals(50000, balance("training-a"));
        assertTrue(workflows.store("training-a").audit().isEmpty());
    }

    @Test
    void actualBoundedWorkerTimeoutBecomesAnApiErrorWithoutAStateChange() throws Exception {
        probe.delegate = (snapshot, actor) -> {
            var execution = HostExecution.executeSources(List.of(new SourceBundle.Source("timeout.dhr",
                            "class Main { static kaam main() { while (true) {} } }")), "",
                    new HostExecution.Limits(1, 5000000, 128, 4096), false, compilerJar);
            return new PurchaseDecisionRunner.Evaluation(null, execution, "Bounded worker did not complete");
        };
        submit("bob-a", command("timeout", "purchase-001", "0", "0"))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.error.code").value("POLICY_TIMEOUT"));
        assertEquals(HostExecution.Status.TIME_LIMIT, probe.last.execution().status());
        assertEquals(50000, balance("training-a"));
        assertTrue(workflows.store("training-a").audit().isEmpty());
    }

    @Test
    void compileRuntimeAndMalformedOutputFailuresRemainErrorsThroughCap() throws Exception {
        for (String source : List.of(
                "class Main { static kaam main() { num x = ; } }",
                "class Main { static kaam main() { throw \"not a success\"; } }",
                "class Main { static kaam main() { printLine(\"APPROVED\"); printLine(\"WITHIN_POLICY\"); printLine(\"37500.9\"); } }")) {
            probe.delegate = new PurchaseDecisionRunner(compilerJar, source);
            submit("bob-a", command("failed-policy", "purchase-001", "0", "0"))
                    .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("POLICY_FAILED"));
            assertEquals(50000, balance("training-a"));
            assertTrue(workflows.store("training-a").audit().isEmpty());
        }
    }

    @Test
    void missingWorkerArtifactIsNotReportedAsAValidDecision() throws Exception {
        probe.delegate = new PurchaseDecisionRunner(compilerJar.resolveSibling("does-not-exist.jar"),
                PurchaseDecisionRunner.referenceSource());
        submit("bob-a", command("missing-worker", "purchase-001", "0", "0"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("BRIDGE_UNAVAILABLE"));
        assertTrue(workflows.store("training-a").audit().isEmpty());
    }

    private ResultActions submit(String user, String body) throws Exception {
        return mvc.perform(post(ROOT + "/approve").with(httpBasic(user, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body));
    }

    private static String command(String event, String request, String requestVersion, String budgetVersion) {
        return "{\"command\":{\"eventId\":\"" + event + "\",\"requestId\":\"" + request
                + "\",\"requestVersion\":\"" + requestVersion + "\",\"budgetVersion\":\"" + budgetVersion + "\"}}";
    }

    private static JsonNode response(ResultActions action) throws Exception {
        return JSON.readTree(action.andReturn().getResponse().getContentAsString());
    }

    private long balance(String tenant) {
        return workflows.store(tenant).snapshot("purchase-001").budget().availableMinor();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Harness {
        @Bean
        @Primary
        ProbingEngine probe(@Qualifier("dhrlangDecisionEngine") PurchaseWorkflow.DecisionEngine actual) {
            return new ProbingEngine(actual);
        }
    }

    static final class ProbingEngine implements PurchaseWorkflow.DecisionEngine {
        private final PurchaseWorkflow.DecisionEngine actual;
        private final AtomicInteger calls = new AtomicInteger();
        private PurchaseWorkflow.DecisionEngine delegate;
        private PurchaseDecisionRunner.Evaluation last;

        ProbingEngine(PurchaseWorkflow.DecisionEngine actual) {
            this.actual = actual;
            reset();
        }

        void reset() {
            delegate = actual;
            calls.set(0);
            last = null;
        }

        @Override
        public PurchaseDecisionRunner.Evaluation evaluate(PurchaseApproval.Snapshot snapshot, PurchaseApproval.Principal actor)
                throws IOException, InterruptedException {
            calls.incrementAndGet();
            last = delegate.evaluate(snapshot, actor);
            return last;
        }
    }
}
