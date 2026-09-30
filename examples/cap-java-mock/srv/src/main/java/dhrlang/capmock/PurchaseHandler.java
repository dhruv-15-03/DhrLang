package dhrlang.capmock;

import cds.gen.purchaselab.ApprovalCommand;
import cds.gen.purchaselab.ApprovalResult;
import cds.gen.purchaselab.ApproveContext;
import cds.gen.purchaselab.PurchaseLab_;
import cds.gen.purchaselab.Receipt;
import cds.gen.purchaselab.Snapshot;
import cds.gen.purchaselab.SnapshotContext;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import dhrlang.enterprise.PurchaseWorkflow;
import dhrlang.host.HostExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ServiceName(PurchaseLab_.CDS_NAME)
public final class PurchaseHandler implements EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PurchaseHandler.class);
    private final TenantWorkflows workflows;

    PurchaseHandler(TenantWorkflows workflows) {
        this.workflows = workflows;
    }

    @On(event = ApproveContext.CDS_NAME)
    public void approve(ApproveContext context) {
        PurchaseWorkflow.Command command = command(context.getCommand());
        PurchaseWorkflow.Result result;
        try {
            result = workflows.approve(context.getUserInfo(), command);
        } catch (IOException failure) {
            LOG.error("Local DhrLang bridge I/O failure", failure);
            throw new ServiceException(AdapterError.BRIDGE_UNAVAILABLE, "The local decision runner is unavailable");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            LOG.warn("Local DhrLang bridge interrupted");
            throw new ServiceException(AdapterError.BRIDGE_UNAVAILABLE, "The local decision runner was interrupted");
        }
        switch (result.status()) {
            case FORBIDDEN -> throw new ServiceException(AdapterError.HOST_FORBIDDEN, result.message());
            case CONFLICT -> throw new ServiceException(AdapterError.VERSION_OR_INTENT_CONFLICT, result.message());
            case POLICY_MISMATCH -> throw new ServiceException(AdapterError.POLICY_MISMATCH,
                    "The proposal did not match the host policy; no update applied");
            case POLICY_FAILED -> {
                boolean timeout = result.evaluation().execution().status() == HostExecution.Status.TIME_LIMIT;
                LOG.warn("Local policy failed with status {}", result.evaluation().execution().status());
                throw new ServiceException(timeout ? AdapterError.POLICY_TIMEOUT : AdapterError.POLICY_FAILED,
                        "The policy did not complete successfully; no update applied");
            }
            case RETRYABLE -> throw new ServiceException(AdapterError.STORE_RETRYABLE, result.message());
            case RECONCILIATION_REQUIRED -> throw new ServiceException(AdapterError.RECONCILIATION_REQUIRED,
                    "Commit outcome is uncertain; reconcile the original event before any new write");
            case APPLIED, REJECTED, REPLAYED -> {
                ApprovalResult response = ApprovalResult.create();
                response.setProfile(LocalCapMockApplication.PROFILE);
                response.setStatus(result.status().name());
                response.setMessage(result.message());
                response.setReceipt(receipt(result.receipt()));
                context.setResult(response);
            }
        }
    }

    @On(event = SnapshotContext.CDS_NAME)
    public void snapshot(SnapshotContext context) {
        var actor = workflows.principal(context.getUserInfo());
        var value = workflows.snapshot(actor, context.getRequestId());
        Snapshot response = Snapshot.create();
        response.setProfile(LocalCapMockApplication.PROFILE);
        response.setTenant(actor.tenant());
        response.setRequestId(value.request().id());
        response.setState(value.request().state().name());
        response.setRequestVersion(Long.toString(value.request().version()));
        response.setBudgetVersion(Long.toString(value.budget().version()));
        response.setBudgetMinor(value.budget().availableMinor());
        response.setCurrency(value.budget().currency());
        context.setResult(response);
    }

    private static PurchaseWorkflow.Command command(ApprovalCommand input) {
        if (input == null || input.getRequestVersion() == null || input.getBudgetVersion() == null) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "The command and both exact integer revisions are required");
        }
        try {
            return new PurchaseWorkflow.Command(input.getEventId(), input.getRequestId(),
                    exactRevision(input.getRequestVersion()), exactRevision(input.getBudgetVersion()));
        } catch (IllegalArgumentException failure) {
            throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Invalid event, request or revision");
        }
    }

    private static long exactRevision(String token) {
        if (token == null || token.length() > 19 || !token.matches("0|[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("A canonical nonnegative integer revision is required");
        }
        return Long.parseLong(token);
    }

    private static Receipt receipt(PurchaseWorkflow.Receipt value) {
        Receipt result = Receipt.create();
        result.setEventId(value.command().eventId());
        result.setRequestId(value.command().requestId());
        result.setTenant(value.tenant());
        result.setActor(value.actor());
        result.setOutcome(value.decision().outcome().name());
        result.setReason(value.decision().reason().name());
        result.setBefore(value.before().name());
        result.setAfter(value.after().name());
        result.setRequestVersion(Long.toString(value.requestVersion()));
        result.setBudgetVersion(Long.toString(value.budgetVersion()));
        result.setBudgetMinor(value.budgetMinor());
        result.setReservedMinor(value.reservedMinor());
        result.setCompilerVersion(value.compilerVersion());
        result.setSourceSha256(value.sourceSha256());
        return result;
    }
}
