package dhrlang.enterprise;

import dhrlang.host.HostExecution;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static dhrlang.enterprise.PurchaseApproval.*;
import static dhrlang.enterprise.PurchaseWorkflow.*;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseWorkflowTest {
    private static final Principal BOB = new Principal("training", "bob", true, Set.of("engineering"), "INR", 50000);

    private static Request request(String id, long amount) {
        return new Request(id, "alice", "supplier", "engineering", "INR", amount, true, State.SUBMITTED, 0);
    }

    private static Store store(Request... requests) {
        return new Store("training", List.of(requests), List.of(new Budget("engineering", "INR", 50000, 0)));
    }

    private static Command command(String event, String request) {
        return new Command(event, request, 0, 0);
    }

    private static PurchaseDecisionRunner.Evaluation reference(Snapshot snapshot, Principal actor) {
        return evidence(evaluate(snapshot, actor));
    }

    private static PurchaseDecisionRunner.Evaluation evidence(Decision decision) {
        // A fake engine isolates transaction tests; independent DhrLang/Java agreement is tested separately.
        var execution = new HostExecution.Response(1, HostExecution.PROFILE, "test-fixture", "f".repeat(64),
                HostExecution.Status.SUCCESS, "", "", List.of(), "Synthetic reference", 0, 0);
        return new PurchaseDecisionRunner.Evaluation(decision, execution, "");
    }

    @Test
    void approvalConservesBudgetAndDuplicateDeliveryDoesNotReserveAgain() throws Exception {
        Store store = store(request("one", 12500), request("two", 12500));
        AtomicInteger calls = new AtomicInteger();
        var workflow = new PurchaseWorkflow(store, (snapshot, actor) -> {
            calls.incrementAndGet();
            return reference(snapshot, actor);
        });
        Command command = command("event", "one");
        Result first = workflow.approve(command, BOB);
        Result replay = workflow.approve(command, BOB);
        assertEquals(Status.APPLIED, first.status());
        assertEquals(Status.REPLAYED, replay.status());
        assertEquals(first.receipt(), replay.receipt());
        assertEquals(1, calls.get());
        assertEquals(37500, store.snapshot("one").budget().availableMinor());
        assertEquals(12500, first.receipt().reservedMinor());
        assertEquals(50000, Math.addExact(first.receipt().budgetMinor(), first.receipt().reservedMinor()));
        assertEquals("training", first.receipt().tenant());
        assertEquals("bob", first.receipt().actor());
        assertEquals(12500, first.receipt().payload().amountMinor());
        assertEquals("INR", first.receipt().payload().currency());
        assertEquals("INR", first.receipt().payload().budgetCurrency());
        assertEquals(1, store.audit().size());
        assertEquals(Status.CONFLICT, workflow.approve(command("event", "two"), BOB).status());
        assertEquals(Status.CONFLICT, workflow.approve(new Command("event", "one", 1, 1), BOB).status());
        assertEquals(1, calls.get());
    }

    @Test
    void unauthorizedActorsNeverReachThePolicyOrChangeState() throws Exception {
        Store store = store(request("one", 12500));
        var workflow = new PurchaseWorkflow(store, (snapshot, actor) -> {
            fail("An unauthorized caller reached the decision engine");
            return null;
        });
        for (Principal actor : List.of(
                new Principal("training", "alice", true, Set.of("engineering"), "INR", 50000),
                new Principal("training", "bob", false, Set.of("engineering"), "INR", 50000),
                new Principal("training", "bob", true, Set.of("sales"), "INR", 50000),
                new Principal("another-tenant", "bob", true, Set.of("engineering"), "INR", 50000))) {
            assertEquals(Status.FORBIDDEN, workflow.approve(command("event", "one"), actor).status());
        }
        assertEquals(Status.FORBIDDEN, workflow.approve(command("event", "unknown"),
                new Principal("another-tenant", "bob", true, Set.of("engineering"), "INR", 50000)).status());
        assertEquals(State.SUBMITTED, store.snapshot("one").request().state());
        assertEquals(50000, store.snapshot("one").budget().availableMinor());
        assertTrue(store.audit().isEmpty());
    }

    @Test
    void invalidBusinessInputsAreRejectedBeforeStateOrBudgetTransitions() throws Exception {
        for (Request invalid : List.of(request("one", -1), request("one", 0), request("one", 50001),
                new Request("one", "alice", "supplier", "engineering", "INR", 12500, false, State.SUBMITTED, 0),
                new Request("one", "alice", "supplier", "engineering", "USD", 12500, true, State.SUBMITTED, 0))) {
            Store store = store(invalid);
            var workflow = new PurchaseWorkflow(store, PurchaseWorkflowTest::reference);
            Result result = workflow.approve(command("event", "one"), BOB);
            assertEquals(Status.REJECTED, result.status());
            assertEquals(invalid, store.snapshot("one").request());
            assertEquals(50000, store.snapshot("one").budget().availableMinor());
            assertEquals(0, result.receipt().reservedMinor());
            assertEquals(Status.REPLAYED, workflow.approve(command("event", "one"), BOB).status());
            assertEquals(1, store.audit().size());
        }
    }

    @Test
    void reviewDoesNotReserveMoneyAndAQualifiedSecondApproverCanFinish() throws Exception {
        Store store = store(request("one", 12500));
        var workflow = new PurchaseWorkflow(store, PurchaseWorkflowTest::reference);
        Principal reviewer = new Principal("training", "bob", true, Set.of("engineering"), "INR", 10000);
        Result review = workflow.approve(command("review", "one"), reviewer);
        assertEquals(Status.APPLIED, review.status());
        assertEquals(Outcome.NEEDS_REVIEW, review.receipt().decision().outcome());
        assertEquals(0, review.receipt().reservedMinor());
        assertEquals(State.NEEDS_REVIEW, store.snapshot("one").request().state());
        assertEquals(0, store.snapshot("one").budget().version());
        Principal approver = new Principal("training", "finance", true, Set.of("engineering"), "INR", 50000);
        Result approval = workflow.approve(new Command("approve", "one", 1, 0), approver);
        assertEquals(Status.APPLIED, approval.status());
        assertEquals(State.APPROVED, approval.receipt().after());
        assertEquals(2, approval.receipt().requestVersion());
        assertEquals(37500, approval.receipt().budgetMinor());
    }

    @Test
    void revisionsAreCheckedAgainAfterTheDecisionReturns() throws Exception {
        Store store = store(request("one", 12500));
        Principal reviewer = new Principal("training", "bob", true, Set.of("engineering"), "INR", 10000);
        var concurrent = new PurchaseWorkflow(store, PurchaseWorkflowTest::reference);
        var workflow = new PurchaseWorkflow(store, (snapshot, actor) -> {
            assertEquals(Status.APPLIED, concurrent.approve(command("concurrent", "one"), actor).status());
            return reference(snapshot, actor);
        });
        Result stale = workflow.approve(command("stale", "one"), reviewer);
        assertEquals(Status.CONFLICT, stale.status());
        assertEquals(1, store.audit().size());
        assertEquals(State.NEEDS_REVIEW, store.snapshot("one").request().state());
        assertEquals(50000, store.snapshot("one").budget().availableMinor());
    }

    @Test
    void aProgramCannotOverrideHostPolicyOrCreateAnInvalidDelta() throws Exception {
        for (Decision falseDecision : List.of(
                new Decision(Outcome.APPROVED, Reason.WITHIN_POLICY, 0),
                new Decision(Outcome.APPROVED, Reason.WITHIN_POLICY, 50000),
                new Decision(Outcome.REJECTED, Reason.EXCEEDS_BUDGET, 50000))) {
            Store store = store(request("one", 12500));
            var workflow = new PurchaseWorkflow(store, (snapshot, actor) -> evidence(falseDecision));
            assertEquals(Status.POLICY_MISMATCH, workflow.approve(command("event", "one"), BOB).status());
            assertTrue(store.audit().isEmpty());
            assertEquals(50000, store.snapshot("one").budget().availableMinor());
        }
    }

    @Test
    void preCommitFailureIsRetryableWithoutAnInventedReceipt() throws Exception {
        Store store = store(request("one", 12500));
        var workflow = new PurchaseWorkflow(store, PurchaseWorkflowTest::reference);
        store.failNextCommit(Fault.BEFORE_COMMIT);
        Result failed = workflow.approve(command("event", "one"), BOB);
        assertEquals(Status.RETRYABLE, failed.status());
        assertNull(failed.receipt());
        assertTrue(store.audit().isEmpty());
        assertEquals(50000, store.snapshot("one").budget().availableMinor());
        assertEquals(Status.APPLIED, workflow.approve(command("event", "one"), BOB).status());
        assertEquals(1, store.audit().size());
    }

    @Test
    void lostAcknowledgementRequiresReconciliationWithTheSameAuthorizedIntent() throws Exception {
        Store store = store(request("one", 12500), request("two", 1000));
        var workflow = new PurchaseWorkflow(store, PurchaseWorkflowTest::reference);
        Command command = command("event", "one");
        store.failNextCommit(Fault.AFTER_COMMIT);
        Result unknown = workflow.approve(command, BOB);
        assertEquals(Status.RECONCILIATION_REQUIRED, unknown.status());
        assertNull(unknown.receipt());
        assertEquals(1, store.audit().size());
        Principal otherActor = new Principal("training", "carol", true, Set.of("engineering"), "INR", 50000);
        assertEquals(Status.CONFLICT, workflow.approve(command, otherActor).status());
        assertEquals(Status.CONFLICT, workflow.approve(command("event", "two"), BOB).status());
        assertEquals(Status.CONFLICT, workflow.approve(command,
                new Principal("training", "bob", true, Set.of("engineering"), "USD", 50000)).status());
        assertEquals(Status.CONFLICT, workflow.approve(command,
                new Principal("training", "bob", true, Set.of("engineering"), "INR", 50001)).status());
        for (Principal forbidden : List.of(
                new Principal("another-tenant", "bob", true, Set.of("engineering"), "INR", 50000),
                new Principal("training", "bob", false, Set.of("engineering"), "INR", 50000),
                new Principal("training", "bob", true, Set.of("sales"), "INR", 50000))) {
            Result result = workflow.approve(command, forbidden);
            assertEquals(Status.FORBIDDEN, result.status());
            assertNull(result.receipt());
        }
        Result recovered = workflow.approve(command, BOB);
        assertEquals(Status.REPLAYED, recovered.status());
        assertEquals(command, recovered.receipt().command());
        assertEquals("training", recovered.receipt().tenant());
        assertEquals("bob", recovered.receipt().actor());
        assertEquals(12500, recovered.receipt().payload().amountMinor());
        assertEquals(37500, recovered.receipt().budgetMinor());
        assertEquals(1, store.audit().size());
    }

    @Test
    void revisionExhaustionCannotWrapAndFailedExecutionCannotCommit() throws Exception {
        var exhausted = new Request("one", "alice", "supplier", "engineering", "INR", 12500, true, State.SUBMITTED, Long.MAX_VALUE);
        Store store = store(exhausted);
        var workflow = new PurchaseWorkflow(store, (snapshot, actor) -> {
            fail("Exhausted revisions must be rejected before execution");
            return null;
        });
        assertEquals(Status.CONFLICT, workflow.approve(new Command("event", "one", Long.MAX_VALUE, 0), BOB).status());
        Store other = store(request("one", 12500));
        var failed = new PurchaseWorkflow(other, (snapshot, actor) -> new PurchaseDecisionRunner.Evaluation(
                null, new HostExecution.Response(1, HostExecution.PROFILE, "test", "f".repeat(64),
                HostExecution.Status.TIME_LIMIT, "", "", List.of(), "Timed out", -1, 0), "Timed out"));
        assertEquals(Status.POLICY_FAILED, failed.approve(command("event", "one"), BOB).status());
        assertTrue(other.audit().isEmpty());
    }

    @Test
    void realDhrlangRunsTheMockApprovalReplayAndReconciliationDemo() throws Exception {
        var runner = new PurchaseDecisionRunner(Path.of(System.getProperty("dhrlang.test.jar")),
                PurchaseDecisionRunner.referenceSource());
        var demo = EnterpriseCli.demo(runner);
        assertTrue(demo.mockOnly());
        assertTrue(demo.passed(), demo.steps().toString());
        assertEquals(2, demo.audit().size());
        assertEquals(36500, demo.remainingBudgetMinor());
        assertEquals(13500, demo.audit().stream().mapToLong(Receipt::reservedMinor).reduce(0, Math::addExact));
    }
}
