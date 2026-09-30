package dhrlang.capmock;

import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.request.UserInfo;
import dhrlang.enterprise.PurchaseApproval;
import dhrlang.enterprise.PurchaseWorkflow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
final class TenantWorkflows {
    static final long PRECISE_VERSION = 9_007_199_254_740_993L;
    private static final Set<String> REQUESTS = Set.of("purchase-001", "invalid-amount", "precise-version");
    private record Identity(String tenant, String actor) {}
    private record Grant(Set<String> costCenters, long limit) {}
    private static final Map<Identity, Grant> GRANTS = Map.of(
            new Identity("training-a", "bob-a"), new Grant(Set.of("engineering", "precision"), 50000),
            new Identity("training-a", "alice-a"), new Grant(Set.of("engineering"), 50000),
            new Identity("training-a", "reviewer-a"), new Grant(Set.of("engineering", "precision"), 10000),
            new Identity("training-a", "sales-a"), new Grant(Set.of("sales"), 50000),
            new Identity("training-b", "bob-b"), new Grant(Set.of("engineering", "precision"), 50000));

    private final PurchaseWorkflow.DecisionEngine engine;
    private volatile Map<String, PurchaseWorkflow.Store> stores;

    TenantWorkflows(PurchaseWorkflow.DecisionEngine engine) {
        this.engine = engine;
        reset();
    }

    PurchaseApproval.Principal principal(UserInfo user) {
        if (!user.isAuthenticated() || !user.hasRole("Approver")) {
            throw new ServiceException(AdapterError.HOST_FORBIDDEN, "An authenticated approver is required");
        }
        Grant grant = GRANTS.get(new Identity(user.getTenant(), user.getName()));
        if (grant == null) {
            throw new ServiceException(AdapterError.HOST_FORBIDDEN, "No local grant exists for this authenticated actor and tenant");
        }
        return new PurchaseApproval.Principal(user.getTenant(), user.getName(), true, grant.costCenters(), "INR", grant.limit());
    }

    PurchaseApproval.Snapshot snapshot(PurchaseApproval.Principal actor, String requestId) {
        requireIdentifier(requestId);
        if (!REQUESTS.contains(requestId)) {
            throw new ServiceException(ErrorStatuses.NOT_FOUND, "Unknown local purchase fixture");
        }
        PurchaseApproval.Snapshot snapshot = store(actor.tenant()).snapshot(requestId);
        if (!actor.costCenters().contains(snapshot.request().costCenter())) {
            throw new ServiceException(AdapterError.HOST_FORBIDDEN, "This cost center is outside the authenticated grant");
        }
        return snapshot;
    }

    PurchaseWorkflow.Result approve(UserInfo user, PurchaseWorkflow.Command command) throws IOException, InterruptedException {
        PurchaseApproval.Principal actor = principal(user);
        snapshot(actor, command.requestId());
        return new PurchaseWorkflow(store(actor.tenant()), engine).approve(command, actor);
    }

    PurchaseWorkflow.Store store(String tenant) {
        PurchaseWorkflow.Store store = stores.get(tenant);
        if (store == null) throw new ServiceException(AdapterError.HOST_FORBIDDEN, "Unconfigured local tenant");
        return store;
    }

    void reset() {
        stores = Map.of("training-a", seed("training-a", "alice-a", 50000),
                "training-b", seed("training-b", "alice-b", 80000));
    }

    private static PurchaseWorkflow.Store seed(String tenant, String requester, long budget) {
        return new PurchaseWorkflow.Store(tenant, List.of(
                new PurchaseApproval.Request("purchase-001", requester, "supplier-a", "engineering", "INR",
                        12500, true, PurchaseApproval.State.SUBMITTED, 0),
                new PurchaseApproval.Request("invalid-amount", requester, "supplier-a", "engineering", "INR",
                        -100, true, PurchaseApproval.State.SUBMITTED, 0),
                new PurchaseApproval.Request("precise-version", requester, "supplier-a", "precision", "INR",
                        12500, true, PurchaseApproval.State.SUBMITTED, PRECISE_VERSION)),
                List.of(new PurchaseApproval.Budget("engineering", "INR", budget, 0),
                        new PurchaseApproval.Budget("precision", "INR", 50000, PRECISE_VERSION)));
    }

    private static void requireIdentifier(String value) {
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9_-]+")) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Invalid purchase identifier");
        }
    }
}
