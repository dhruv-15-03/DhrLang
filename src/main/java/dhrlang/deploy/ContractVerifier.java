package dhrlang.deploy;

import dhrlang.evm.EvmContractCompiler.ContractArtifact;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Verifies deployed contracts on block explorers (Etherscan, Arbiscan, etc.).
 *
 * <p>Submits source code (or bytecode) for verification so users can read/interact
 * with the contract on-chain through the explorer UI.</p>
 *
 * <p>Supports:
 * <ul>
 *   <li>Etherscan API (mainnet + testnets)</li>
 *   <li>Arbiscan API</li>
 *   <li>Basescan API</li>
 *   <li>Polygonscan API</li>
 *   <li>Optimistic Etherscan API</li>
 * </ul>
 *
 * <p><b>User story:</b> As a developer, I want to verify my deployed contract on
 * Etherscan so others can read the source code.</p>
 */
public final class ContractVerifier {

    // ── Constants ────────────────────────────────────────────────────────

    private static final String ENV_ETHERSCAN_KEY = "DHRLANG_ETHERSCAN_API_KEY";
    private static final int TIMEOUT_MS = 30_000;
    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final long POLL_INTERVAL_MS = 5_000;

    // ── Verification Status ──────────────────────────────────────────────

    /**
     * Result of a verification attempt.
     */
    public static final class VerificationResult {
        private final boolean success;
        private final String guid;
        private final String message;
        private final String explorerUrl;

        VerificationResult(boolean success, String guid, String message, String explorerUrl) {
            this.success = success;
            this.guid = guid;
            this.message = message;
            this.explorerUrl = explorerUrl;
        }

        public boolean isSuccess() { return success; }
        public String getGuid() { return guid; }
        public String getMessage() { return message; }
        public String getExplorerUrl() { return explorerUrl; }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private String apiKey;
    private L2ChainConfig chain;
    private boolean dryRun = false;

    // ── Configuration ────────────────────────────────────────────────────

    public ContractVerifier() {
        this.chain = L2ChainConfig.ETHEREUM_MAINNET;
        this.apiKey = System.getenv(ENV_ETHERSCAN_KEY);
    }

    public ContractVerifier setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public ContractVerifier setChain(L2ChainConfig chain) {
        this.chain = chain;
        return this;
    }

    public ContractVerifier setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Verify a deployed contract using its compiled artifact and deployment address.
     *
     * @param artifact        the compiled contract artifact (contains ABI + bytecode)
     * @param contractAddress the deployed contract address (0x...)
     * @param sourceCode      the original DhrLang source code
     * @return verification result
     */
    public VerificationResult verify(ContractArtifact artifact, String contractAddress,
                                     String sourceCode) {
        if (apiKey == null || apiKey.isBlank()) {
            return new VerificationResult(false, null,
                    "No API key. Set " + ENV_ETHERSCAN_KEY + " environment variable "
                    + "or pass --etherscan-key=<key>.",
                    null);
        }

        String apiUrl = getApiUrl();
        if (apiUrl == null) {
            return new VerificationResult(false, null,
                    "No API URL known for chain: " + chain.getName(), null);
        }

        // Build verification request
        Map<String, String> params = new LinkedHashMap<>();
        params.put("apikey", apiKey);
        params.put("module", "contract");
        params.put("action", "verifysourcecode");
        params.put("contractaddress", contractAddress);
        params.put("sourceCode", sourceCode);
        params.put("codeformat", "solidity-single-file");
        params.put("contractname", artifact.getContractName());
        params.put("compilerversion", "DhrLang-2.0.0");
        params.put("optimizationUsed", "1");
        params.put("runs", "200");
        params.put("constructorArguements", ""); // sic — Etherscan API uses this spelling
        params.put("evmversion", "shanghai");

        if (dryRun) {
            return new VerificationResult(true, "dry-run",
                    "Dry run — would submit verification to " + apiUrl + " for "
                    + artifact.getContractName() + " at " + contractAddress,
                    chain.getExplorerUrl() + "/address/" + contractAddress);
        }

        // Submit verification
        try {
            String response = httpPost(apiUrl, params);
            // Parse response for GUID
            String guid = extractJsonField(response, "result");
            String status = extractJsonField(response, "status");

            if ("1".equals(status) && guid != null) {
                // Poll for completion
                return pollVerificationStatus(apiUrl, guid, contractAddress);
            } else {
                String msg = extractJsonField(response, "result");
                return new VerificationResult(false, null,
                        "Verification submission failed: " + (msg != null ? msg : response),
                        null);
            }
        } catch (IOException e) {
            return new VerificationResult(false, null,
                    "Network error during verification: " + e.getMessage(), null);
        }
    }

    /**
     * Check verification status of a previously submitted request.
     */
    public VerificationResult checkStatus(String guid) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) {
            return new VerificationResult(false, guid, "No API URL for chain: " + chain.getName(), null);
        }
        try {
            return pollVerificationStatus(apiUrl, guid, null);
        } catch (IOException e) {
            return new VerificationResult(false, guid, "Network error: " + e.getMessage(), null);
        }
    }

    /**
     * Generate a verification command for manual execution.
     */
    public String generateVerifyCommand(ContractArtifact artifact, String contractAddress) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DhrLang Contract Verification\n");
        sb.append("# Contract: ").append(artifact.getContractName()).append('\n');
        sb.append("# Address: ").append(contractAddress).append('\n');
        sb.append("# Chain: ").append(chain.getName()).append(" (").append(chain.getChainId()).append(")\n\n");

        // Foundry verify command
        sb.append("# Option 1: Using Foundry\n");
        sb.append("forge verify-contract \\\n");
        sb.append("  --chain-id ").append(chain.getChainId()).append(" \\\n");
        sb.append("  --compiler-version DhrLang-2.0.0 \\\n");
        sb.append("  ").append(contractAddress).append(" \\\n");
        sb.append("  ").append(artifact.getContractName()).append('\n');
        sb.append('\n');

        // Etherscan API curl command
        sb.append("# Option 2: Using Etherscan API directly\n");
        String apiUrl = getApiUrl();
        sb.append("curl -X POST '").append(apiUrl != null ? apiUrl : "<API_URL>").append("' \\\n");
        sb.append("  -d 'module=contract' \\\n");
        sb.append("  -d 'action=verifysourcecode' \\\n");
        sb.append("  -d 'apikey=$DHRLANG_ETHERSCAN_API_KEY' \\\n");
        sb.append("  -d 'contractaddress=").append(contractAddress).append("' \\\n");
        sb.append("  -d 'contractname=").append(artifact.getContractName()).append("' \\\n");
        sb.append("  -d 'compilerversion=DhrLang-2.0.0' \\\n");
        sb.append("  -d 'sourceCode=@contract.dhr'\n");
        sb.append('\n');

        // DhrLang CLI command
        sb.append("# Option 3: Using DhrLang CLI\n");
        sb.append("java -jar DhrLang-2.0.0.jar --verify \\\n");
        sb.append("  --address=").append(contractAddress).append(" \\\n");
        sb.append("  --network=").append(chain.getName().toLowerCase().replace(" ", "-")).append(" \\\n");
        sb.append("  contract.dhr\n");

        return sb.toString();
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private String getApiUrl() {
        String explorer = chain.getExplorerUrl();
        if (explorer == null) return null;

        // Map known explorer URLs to API endpoints
        Map<String, String> apiUrls = new LinkedHashMap<>();
        apiUrls.put("https://etherscan.io", "https://api.etherscan.io/api");
        apiUrls.put("https://sepolia.etherscan.io", "https://api-sepolia.etherscan.io/api");
        apiUrls.put("https://arbiscan.io", "https://api.arbiscan.io/api");
        apiUrls.put("https://sepolia.arbiscan.io", "https://api-sepolia.arbiscan.io/api");
        apiUrls.put("https://basescan.org", "https://api.basescan.org/api");
        apiUrls.put("https://sepolia.basescan.org", "https://api-sepolia.basescan.org/api");
        apiUrls.put("https://optimistic.etherscan.io", "https://api-optimistic.etherscan.io/api");
        apiUrls.put("https://polygonscan.com", "https://api.polygonscan.com/api");

        return apiUrls.get(explorer);
    }

    private VerificationResult pollVerificationStatus(String apiUrl, String guid,
                                                       String contractAddress) throws IOException {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            String checkUrl = apiUrl + "?module=contract&action=checkverifystatus&guid=" + guid
                    + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

            String response = httpGet(checkUrl);
            String result = extractJsonField(response, "result");

            if (result != null) {
                if (result.contains("Pass")) {
                    String explorerLink = contractAddress != null
                            ? chain.getExplorerUrl() + "/address/" + contractAddress + "#code"
                            : null;
                    return new VerificationResult(true, guid,
                            "Contract verified successfully!", explorerLink);
                } else if (result.contains("Fail")) {
                    return new VerificationResult(false, guid,
                            "Verification failed: " + result, null);
                }
                // Still pending — continue polling
            }
        }

        return new VerificationResult(false, guid,
                "Verification timed out after " + MAX_POLL_ATTEMPTS + " attempts. "
                + "Check status with GUID: " + guid, null);
    }

    private static String httpPost(String url, Map<String, String> params) throws IOException {
        StringBuilder postData = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (postData.length() > 0) postData.append('&');
            postData.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        byte[] postBytes = postData.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postBytes);
        }

        return readResponse(conn);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return readResponse(conn);
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Simple JSON field extraction (no full parser dependency).
     */
    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        }
        // Non-string value
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }
}
