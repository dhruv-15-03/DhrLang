package dhrlang.capmock;

import com.sap.cds.services.ErrorStatus;

enum AdapterError implements ErrorStatus {
    HOST_FORBIDDEN(403),
    VERSION_OR_INTENT_CONFLICT(409),
    POLICY_FAILED(502),
    POLICY_MISMATCH(502),
    POLICY_TIMEOUT(504),
    STORE_RETRYABLE(503),
    RECONCILIATION_REQUIRED(503),
    BRIDGE_UNAVAILABLE(503);

    private final int status;

    AdapterError(int status) {
        this.status = status;
    }

    @Override
    public String getCodeString() {
        return name();
    }

    @Override
    public int getHttpStatus() {
        return status;
    }
}
