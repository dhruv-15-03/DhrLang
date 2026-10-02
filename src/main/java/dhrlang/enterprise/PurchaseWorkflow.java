package dhrlang.enterprise;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dhrlang.enterprise.PurchaseApproval.*;

/** In-memory teaching adapter. It is not durable storage, SAP integration or an authentication service. */
public final class PurchaseWorkflow {
    public enum Status { APPLIED, REJECTED, REPLAYED, FORBIDDEN, CONFLICT, POLICY_FAILED, POLICY_MISMATCH, RETRYABLE, RECONCILIATION_REQUIRED }
    public enum Fault { NONE, BEFORE_COMMIT, AFTER_COMMIT }

    public record Command(String eventId, String requestId, long requestVersion, long budgetVersion) {
        public Command {
            if (!identifier(eventId) || !identifier(requestId) || requestVersion < 0 || budgetVersion < 0) {
                throw new IllegalArgumentException("Invalid event/request identity or expected version");
            }
        }
    }

    public record Payload(String requester, String supplier, boolean supplierActive, String costCenter,
                          String currency, long amountMinor, String budgetCurrency,
                          String approvalCurrency, long approvalLimitMinor) {
        private static Payload of(Snapshot snapshot, Principal actor) {
            Request request = snapshot.request();
            return new Payload(request.requester(), request.supplier(), request.supplierActive(), request.costCenter(),
                    request.currency(), request.amountMinor(), snapshot.budget().currency(),
                    actor.approvalCurrency(), actor.approvalLimitMinor());
        }
    }
    public record Receipt(Command command, String tenant, String actor, Payload payload, Decision decision, State before, State after,
                          long requestVersion, long budgetVersion, long budgetMinor, long reservedMinor,
                          String compilerVersion, String sourceSha256) {}
    public record Result(Status status, String message, Receipt receipt, PurchaseDecisionRunner.Evaluation evaluation) {}

    @FunctionalInterface
    public interface DecisionEngine {
        PurchaseDecisionRunner.Evaluation evaluate(Snapshot snapshot, Principal actor) throws IOException, InterruptedException;
    }

    private final Store store;
    private final DecisionEngine engine;

    public PurchaseWorkflow(Store store, DecisionEngine engine) {
        this.store = java.util.Objects.requireNonNull(store);
        this.engine = java.util.Objects.requireNonNull(engine);
    }

    public Result approve(Command command, Principal actor) throws IOException, InterruptedException {
        Snapshot snapshot;
        synchronized (store) {
            Result rejection = store.preflight(command, actor);
            if (rejection != null) return rejection;
            snapshot = store.snapshot(command.requestId());
        }
        var evaluation = engine.evaluate(snapshot, actor);
        if (!evaluation.successful()) {
            return new Result(Status.POLICY_FAILED, evaluation.error(), null, evaluation);
        }
        if (!evaluate(snapshot, actor).equals(evaluation.decision())) {
            return new Result(Status.POLICY_MISMATCH, "DhrLang disagrees with the authoritative Java policy; no update applied",
                    null, evaluation);
        }
        return store.commit(command, actor, evaluation);
    }

    public static final class Store {
        private final String tenant;
        private final Map<String, Request> requests = new LinkedHashMap<>();
        private final Map<String, Budget> budgets = new LinkedHashMap<>();
        private final Map<String, Receipt> receipts = new LinkedHashMap<>();
        private Fault nextFault = Fault.NONE;

        public Store(String tenant, List<Request> requests, List<Budget> budgets) {
            if (!identifier(tenant)) throw new IllegalArgumentException("Invalid store tenant");
            this.tenant = tenant;
            for (Request request : requests) {
                if (this.requests.putIfAbsent(request.id(), request) != null) throw new IllegalArgumentException("Duplicate request");
            }
            for (Budget budget : budgets) {
                if (this.budgets.putIfAbsent(budget.costCenter(), budget) != null) throw new IllegalArgumentException("Duplicate budget");
            }
            for (Request request : requests) snapshot(request.id());
        }

        public synchronized Snapshot snapshot(String requestId) {
            Request request = requests.get(requestId);
            if (request == null) throw new IllegalArgumentException("Unknown purchase request: " + requestId);
            return new Snapshot(request, budgets.get(request.costCenter()));
        }

        public synchronized List<Receipt> audit() {
            return List.copyOf(receipts.values());
        }

        public synchronized void failNextCommit(Fault fault) {
            nextFault = java.util.Objects.requireNonNull(fault);
        }

        private synchronized Result preflight(Command command, Principal actor) {
            if (!tenant.equals(actor.tenant())) {
                return new Result(Status.FORBIDDEN, "Tenant mismatch; no receipt returned or update applied", null, null);
            }
            Snapshot current = snapshot(command.requestId());
            Reason denial = accessDenial(current.request(), actor);
            if (denial != null) return new Result(Status.FORBIDDEN, denial.name() + "; no update applied", null, null);
            Receipt previous = receipts.get(command.eventId());
            if (previous != null) {
                if (previous.command().equals(command) && previous.tenant().equals(actor.tenant())
                        && previous.actor().equals(actor.id()) && previous.payload().equals(Payload.of(current, actor))) {
                    return new Result(Status.REPLAYED, "Existing receipt returned; no second update", previous, null);
                }
                return new Result(Status.CONFLICT, "Idempotency key is bound to a different payload, revision, actor or tenant", null, null);
            }
            if (current.request().version() != command.requestVersion() || current.budget().version() != command.budgetVersion()) {
                return new Result(Status.CONFLICT, "Request or budget version changed; reload before submitting a new event", null, null);
            }
            if (!pending(current.request().state())) {
                return new Result(Status.CONFLICT, "Request is already in a closed state", null, null);
            }
            if (current.request().version() == Long.MAX_VALUE || current.budget().version() == Long.MAX_VALUE) {
                return new Result(Status.CONFLICT, "Revision counter exhausted; no update applied", null, null);
            }
            return null;
        }

        private synchronized Result commit(Command command, Principal actor, PurchaseDecisionRunner.Evaluation evaluation) {
            Result rejection = preflight(command, actor);
            if (rejection != null) return rejection;
            Snapshot current = snapshot(command.requestId());
            Decision authoritative = evaluate(current, actor);
            if (!authoritative.equals(evaluation.decision())) {
                return new Result(Status.POLICY_MISMATCH, "Policy no longer matches current host state; no update applied", null, evaluation);
            }
            boolean approved = authoritative.outcome() == Outcome.APPROVED;
            long reserved = Math.subtractExact(current.budget().availableMinor(), authoritative.remainingBudgetMinor());
            long recomposed = Math.addExact(authoritative.remainingBudgetMinor(), reserved);
            if (reserved < 0 || reserved > MAX_MINOR_UNITS || recomposed < 0 || recomposed > MAX_MINOR_UNITS
                    || recomposed != current.budget().availableMinor()
                    || reserved != (approved ? current.request().amountMinor() : 0)) {
                return new Result(Status.POLICY_MISMATCH, "Reservation delta does not conserve the bounded host budget",
                        null, evaluation);
            }
            Fault fault = nextFault;
            nextFault = Fault.NONE;
            if (fault == Fault.BEFORE_COMMIT) {
                return new Result(Status.RETRYABLE, "Mock store failed before committing; no update applied", null, evaluation);
            }
            Request request = current.request();
            Budget budget = current.budget();
            State nextState = switch (authoritative.outcome()) {
                case APPROVED -> State.APPROVED;
                case NEEDS_REVIEW -> State.NEEDS_REVIEW;
                case REJECTED -> request.state();
            };
            boolean changed = nextState != request.state();
            Request updatedRequest = new Request(request.id(), request.requester(), request.supplier(), request.costCenter(),
                    request.currency(), request.amountMinor(), request.supplierActive(), nextState,
                    changed ? request.version() + 1 : request.version());
            Budget updatedBudget = new Budget(budget.costCenter(), budget.currency(), authoritative.remainingBudgetMinor(),
                    approved ? budget.version() + 1 : budget.version());
            var receipt = new Receipt(command, tenant, actor.id(), Payload.of(current, actor), authoritative, request.state(), nextState,
                    updatedRequest.version(), updatedBudget.version(), updatedBudget.availableMinor(), reserved,
                    evaluation.execution().compilerVersion(), evaluation.execution().sourceSha256());
            requests.put(request.id(), updatedRequest);
            budgets.put(budget.costCenter(), updatedBudget);
            receipts.put(command.eventId(), receipt);
            if (fault == Fault.AFTER_COMMIT) {
                return new Result(Status.RECONCILIATION_REQUIRED,
                        "Commit acknowledgement was lost; reconcile/replay the same event before attempting another write",
                        null, evaluation);
            }
            return new Result(authoritative.outcome() == Outcome.REJECTED ? Status.REJECTED : Status.APPLIED,
                    approved ? "Approved and reserved budget in the in-memory transaction"
                            : authoritative.outcome() == Outcome.REJECTED
                            ? "Rejected without a request-state or budget change; receipt recorded" : "Moved to review without reserving budget",
                    receipt, evaluation);
        }
    }
}
