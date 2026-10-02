package dhrlang.enterprise;

import java.util.Set;

/** Synthetic procurement policy; authentication and persistence belong to the host. */
public final class PurchaseApproval {
    // Current JVM relational comparisons use doubles; stay within their exact integer range.
    public static final long MAX_MINOR_UNITS = 9_007_199_254_740_991L;

    public enum State { SUBMITTED, NEEDS_REVIEW, APPROVED, REJECTED }
    public enum Outcome { APPROVED, REJECTED, NEEDS_REVIEW }
    public enum Reason {
        NOT_AUTHORIZED, WRONG_COST_CENTER, SELF_APPROVAL, INVALID_STATE, INVALID_AMOUNT,
        INVALID_SUPPLIER, CURRENCY_MISMATCH, EXCEEDS_BUDGET, APPROVAL_LIMIT, WITHIN_POLICY
    }

    public record Request(String id, String requester, String supplier, String costCenter, String currency,
                          long amountMinor, boolean supplierActive, State state, long version) {
        public Request {
            require(identifier(id) && identifier(requester) && identifier(supplier) && identifier(costCenter),
                    "Request identifiers must be 1..64 ASCII letters, digits, underscores or hyphens");
            require(validCurrency(currency), "Currency must be a three-letter uppercase tag");
            require(amountMinor >= -MAX_MINOR_UNITS && amountMinor <= MAX_MINOR_UNITS,
                    "Amount exceeds the lab's exact-integer range");
            require(state != null && version >= 0, "Request state/version is invalid");
        }
    }

    public record Budget(String costCenter, String currency, long availableMinor, long version) {
        public Budget {
            require(identifier(costCenter) && validCurrency(currency), "Invalid budget identity");
            require(availableMinor >= 0 && availableMinor <= MAX_MINOR_UNITS && version >= 0,
                    "Budget must be nonnegative and within the lab's exact-integer range");
        }
    }

    /** A simulated trusted host principal, not a credential or an identity provider. */
    public record Principal(String tenant, String id, boolean mayApprove, Set<String> costCenters,
                            String approvalCurrency, long approvalLimitMinor) {
        public Principal {
            require(identifier(tenant) && identifier(id), "Invalid tenant/principal identifier");
            require(costCenters != null && costCenters.size() <= 32
                    && costCenters.stream().allMatch(PurchaseApproval::identifier), "Invalid cost-center scopes");
            require(validCurrency(approvalCurrency), "Approval limit must have a currency tag");
            require(approvalLimitMinor >= 0 && approvalLimitMinor <= MAX_MINOR_UNITS, "Invalid approval limit");
            costCenters = Set.copyOf(costCenters);
        }
    }

    public record Snapshot(Request request, Budget budget) {
        public Snapshot {
            require(request != null && budget != null && request.costCenter().equals(budget.costCenter()),
                    "Request and budget must have the same cost center");
        }
    }

    public record Decision(Outcome outcome, Reason reason, long remainingBudgetMinor) {
        public Decision {
            require(reason != null && outcome == outcomeFor(reason), "Outcome does not match the reason");
            require(remainingBudgetMinor >= 0 && remainingBudgetMinor <= MAX_MINOR_UNITS, "Invalid remaining budget");
        }
    }

    private PurchaseApproval() {}

    public static Reason accessDenial(Request request, Principal actor) {
        if (!actor.mayApprove()) return Reason.NOT_AUTHORIZED;
        if (!actor.costCenters().contains(request.costCenter())) return Reason.WRONG_COST_CENTER;
        if (actor.id().equals(request.requester())) return Reason.SELF_APPROVAL;
        return null;
    }

    public static Decision evaluate(Snapshot snapshot, Principal actor) {
        Request request = snapshot.request();
        Budget budget = snapshot.budget();
        Reason reason = accessDenial(request, actor);
        if (reason == null) {
            if (!pending(request.state())) reason = Reason.INVALID_STATE;
            else if (request.amountMinor() <= 0) reason = Reason.INVALID_AMOUNT;
            else if (!request.supplierActive()) reason = Reason.INVALID_SUPPLIER;
            else if (!request.currency().equals(budget.currency()) || !request.currency().equals(actor.approvalCurrency())) {
                reason = Reason.CURRENCY_MISMATCH;
            }
            else if (request.amountMinor() > budget.availableMinor()) reason = Reason.EXCEEDS_BUDGET;
            else if (request.amountMinor() > actor.approvalLimitMinor()) reason = Reason.APPROVAL_LIMIT;
            else reason = Reason.WITHIN_POLICY;
        }
        long remaining = reason == Reason.WITHIN_POLICY
                ? Math.subtractExact(budget.availableMinor(), request.amountMinor()) : budget.availableMinor();
        return new Decision(outcomeFor(reason), reason, remaining);
    }

    public static boolean pending(State state) {
        return state == State.SUBMITTED || state == State.NEEDS_REVIEW;
    }

    static boolean identifier(String value) {
        return value != null && value.length() <= 64 && value.matches("[A-Za-z0-9_-]+");
    }

    private static boolean validCurrency(String value) {
        return value != null && value.matches("[A-Z]{3}");
    }

    private static Outcome outcomeFor(Reason reason) {
        return switch (reason) {
            case WITHIN_POLICY -> Outcome.APPROVED;
            case APPROVAL_LIMIT -> Outcome.NEEDS_REVIEW;
            default -> Outcome.REJECTED;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
