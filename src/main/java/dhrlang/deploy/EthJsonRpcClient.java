package dhrlang.deploy;

import dhrlang.evm.FunctionSelector;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal Ethereum JSON-RPC client for contract deployment and interaction.
 *
 * <p>Implements the subset of JSON-RPC methods needed for DhrLang's blockchain CLI:
 * <ul>
 *   <li>{@code eth_sendRawTransaction} — broadcast a signed transaction</li>
 *   <li>{@code eth_getTransactionReceipt} — get deployment result</li>
 *   <li>{@code eth_getTransactionCount} — fetch nonce</li>
 *   <li>{@code eth_gasPrice} — fetch current gas price</li>
 *   <li>{@code eth_estimateGas} — estimate gas for a tx</li>
 *   <li>{@code eth_getBalance} — check deployer balance</li>
 *   <li>{@code eth_chainId} — verify we're on the right network</li>
 *   <li>{@code eth_call} — call a view function</li>
 *   <li>{@code eth_getCode} — verify contract deployed</li>
 *   <li>{@code net_version} — network version</li>
 * </ul>
 *
 * <p>Uses only {@code java.net.HttpURLConnection} — no third-party HTTP libraries.</p>
 */
public final class EthJsonRpcClient {

    // ── Constants ────────────────────────────────────────────────────────

    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_RECEIPT_POLLS = 60;
    private static final long POLL_INTERVAL_MS = 2_000;
    private static final String JSON_RPC_VERSION = "2.0";

    // ── State ────────────────────────────────────────────────────────────

    private final String rpcUrl;
    private final int timeoutMs;
    private final AtomicLong requestId = new AtomicLong(1);

    // ── Constructor ──────────────────────────────────────────────────────

    public EthJsonRpcClient(String rpcUrl) {
        this(rpcUrl, DEFAULT_TIMEOUT_MS);
    }

    public EthJsonRpcClient(String rpcUrl, int timeoutMs) {
        if (rpcUrl == null || rpcUrl.isBlank()) {
            throw new RpcException("RPC URL must not be null or blank.");
        }
        this.rpcUrl = rpcUrl;
        this.timeoutMs = timeoutMs;
    }

    // ── Transaction Methods ──────────────────────────────────────────────

    /**
     * Broadcast a signed raw transaction.
     *
     * @param signedTxHex the signed transaction hex (with 0x prefix)
     * @return the transaction hash (0x-prefixed)
     */
    public String sendRawTransaction(String signedTxHex) {
        String result = call("eth_sendRawTransaction", quote(signedTxHex));
        if (result == null || result.isEmpty()) {
            throw new RpcException("eth_sendRawTransaction returned empty result");
        }
        return result;
    }

    /**
     * Wait for a transaction to be mined and return the receipt.
     *
     * @param txHash the transaction hash
     * @return the receipt (parsed into a TxReceipt), or null if timed out
     */
    public TxReceipt waitForReceipt(String txHash) {
        for (int i = 0; i < MAX_RECEIPT_POLLS; i++) {
            String receiptJson = callRaw("eth_getTransactionReceipt", quote(txHash));
            String result = extractField(receiptJson, "result");
            if (result != null && !result.equals("null") && result.contains("blockNumber")) {
                return parseReceipt(result);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null; // timed out
    }

    /**
     * Get the transaction count (nonce) for an address.
     */
    public long getTransactionCount(String address) {
        String result = call("eth_getTransactionCount", quote(address), quote("latest"));
        return parseHexLong(result);
    }

    // ── State Query Methods ──────────────────────────────────────────────

    /**
     * Get the balance of an address in wei.
     */
    public BigInteger getBalance(String address) {
        String result = call("eth_getBalance", quote(address), quote("latest"));
        return parseHexBigInt(result);
    }

    /**
     * Get the current gas price in wei.
     */
    public long getGasPrice() {
        String result = call("eth_gasPrice");
        return parseHexLong(result);
    }

    /**
     * Estimate gas for a contract creation transaction.
     *
     * @param from         the deployer address
     * @param bytecodeHex  the creation bytecode (no 0x prefix)
     * @return estimated gas
     */
    public long estimateGas(String from, String bytecodeHex) {
        String txObj = "{\"from\":" + quote(from) + ",\"data\":\"0x" + bytecodeHex + "\"}";
        String result = call("eth_estimateGas", txObj);
        return parseHexLong(result);
    }

    /**
     * Get the chain ID.
     */
    public long getChainId() {
        String result = call("eth_chainId");
        return parseHexLong(result);
    }

    /**
     * Get deployed contract code (to verify deployment succeeded).
     *
     * @param contractAddress the contract address
     * @return hex-encoded bytecode, or "0x" if nothing deployed
     */
    public String getCode(String contractAddress) {
        return call("eth_getCode", quote(contractAddress), quote("latest"));
    }

    /**
     * Execute a read-only call (view/pure function).
     *
     * @param to       the contract address
     * @param dataHex  the ABI-encoded call data (0x-prefixed)
     * @return the ABI-encoded return data
     */
    public String ethCall(String to, String dataHex) {
        String txObj = "{\"to\":" + quote(to) + ",\"data\":" + quote(dataHex) + "}";
        return call("eth_call", txObj, quote("latest"));
    }

    /**
     * Get the network version.
     */
    public String getNetworkVersion() {
        return call("net_version");
    }

    // ── Convenience: Full Deployment Flow ────────────────────────────────

    /**
     * Deploy a contract: send raw tx, wait for receipt, verify code.
     *
     * @param signedTxHex the signed creation transaction
     * @return deployment result with address, tx hash, gas used
     */
    public DeploymentResult deploy(String signedTxHex) {
        // 1. Send
        String txHash = sendRawTransaction(signedTxHex);

        // 2. Wait for receipt
        TxReceipt receipt = waitForReceipt(txHash);
        if (receipt == null) {
            throw new RpcException("Transaction not mined within timeout (" +
                    (MAX_RECEIPT_POLLS * POLL_INTERVAL_MS / 1000) + "s). Tx: " + txHash);
        }

        if (!receipt.isSuccess()) {
            throw new RpcException("Transaction reverted. Tx: " + txHash +
                    " Status: " + receipt.status);
        }

        // 3. Verify code exists at contract address
        String contractAddress = receipt.contractAddress;
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new RpcException("No contract address in receipt. Tx: " + txHash);
        }

        String code = getCode(contractAddress);
        if (code == null || "0x".equals(code) || code.isEmpty()) {
            throw new RpcException("No code at deployed address " + contractAddress +
                    ". Constructor may have reverted. Tx: " + txHash);
        }

        return new DeploymentResult(txHash, contractAddress, receipt.gasUsed,
                receipt.blockNumber, code.length() / 2 - 1); // -1 for 0x prefix
    }

    // ── Data Classes ─────────────────────────────────────────────────────

    /**
     * Parsed transaction receipt.
     */
    public static final class TxReceipt {
        public final String transactionHash;
        public final String contractAddress;
        public final long gasUsed;
        public final long blockNumber;
        public final String status; // "0x1" = success

        TxReceipt(String transactionHash, String contractAddress,
                  long gasUsed, long blockNumber, String status) {
            this.transactionHash = transactionHash;
            this.contractAddress = contractAddress;
            this.gasUsed = gasUsed;
            this.blockNumber = blockNumber;
            this.status = status;
        }

        public boolean isSuccess() {
            return "0x1".equals(status) || "1".equals(status);
        }
    }

    /**
     * Result of a successful deployment.
     */
    public static final class DeploymentResult {
        public final String txHash;
        public final String contractAddress;
        public final long gasUsed;
        public final long blockNumber;
        public final int deployedCodeSize;

        DeploymentResult(String txHash, String contractAddress,
                         long gasUsed, long blockNumber, int deployedCodeSize) {
            this.txHash = txHash;
            this.contractAddress = contractAddress;
            this.gasUsed = gasUsed;
            this.blockNumber = blockNumber;
            this.deployedCodeSize = deployedCodeSize;
        }
    }

    // ── JSON-RPC Transport ───────────────────────────────────────────────

    private String call(String method, String... params) {
        String raw = callRaw(method, params);
        // Extract "result" field
        String result = extractField(raw, "result");
        if (result == null) {
            // Check for error
            String error = extractField(raw, "error");
            if (error != null) {
                String msg = extractField(error, "message");
                throw new RpcException("RPC error in " + method + ": " + (msg != null ? msg : error));
            }
            throw new RpcException("No result field in response for " + method);
        }
        // Strip quotes if it's a string
        if (result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    private String callRaw(String method, String... params) {
        long id = requestId.getAndIncrement();

        StringBuilder body = new StringBuilder();
        body.append("{\"jsonrpc\":\"").append(JSON_RPC_VERSION).append("\",");
        body.append("\"method\":\"").append(method).append("\",");
        body.append("\"params\":[");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) body.append(',');
            body.append(params[i]);
        }
        body.append("],\"id\":").append(id).append('}');

        try {
            return httpPost(rpcUrl, body.toString());
        } catch (IOException e) {
            throw new RpcException("Network error calling " + method + ": " + e.getMessage());
        }
    }

    private static String httpPost(String url, String jsonBody) throws IOException {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        conn.setReadTimeout(DEFAULT_TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "{}";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ── Parsing Helpers ──────────────────────────────────────────────────

    private static TxReceipt parseReceipt(String json) {
        String txHash = extractStringField(json, "transactionHash");
        String contractAddr = extractStringField(json, "contractAddress");
        long gasUsed = parseHexLong(extractStringField(json, "gasUsed"));
        long blockNum = parseHexLong(extractStringField(json, "blockNumber"));
        String status = extractStringField(json, "status");
        return new TxReceipt(txHash, contractAddr, gasUsed, blockNum, status);
    }

    private static long parseHexLong(String hex) {
        if (hex == null || hex.isEmpty()) return 0;
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (clean.isEmpty()) return 0;
        return Long.parseLong(clean, 16);
    }

    private static BigInteger parseHexBigInt(String hex) {
        if (hex == null || hex.isEmpty()) return BigInteger.ZERO;
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (clean.isEmpty()) return BigInteger.ZERO;
        return new BigInteger(clean, 16);
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    /**
     * Simple JSON field extraction for top-level fields.
     */
    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        char c = json.charAt(start);
        if (c == '"') {
            // String value
            int end = findClosingQuote(json, start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        } else if (c == '{') {
            // Object — find matching brace
            int depth = 1;
            int end = start + 1;
            while (end < json.length() && depth > 0) {
                if (json.charAt(end) == '{') depth++;
                else if (json.charAt(end) == '}') depth--;
                end++;
            }
            return json.substring(start, end);
        } else if (c == 'n' && json.startsWith("null", start)) {
            return null;
        } else {
            // Number or other
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    private static String extractStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        int end = findClosingQuote(json, start + 1);
        return end > start ? json.substring(start + 1, end) : null;
    }

    private static int findClosingQuote(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            if (json.charAt(i) == '"' && json.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }

    // ── Exception ────────────────────────────────────────────────────────

    /**
     * Exception for JSON-RPC errors.
     */
    public static class RpcException extends RuntimeException {
        public RpcException(String message) {
            super(message);
        }
    }
}
