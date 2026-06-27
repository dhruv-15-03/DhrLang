package dhrlang.deploy;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.evm.EvmContractCompiler;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.interop.InteropExporter;
import dhrlang.production.AuditReportGenerator;
import dhrlang.proving.SpecProver;
import dhrlang.stdlib.ContractStdlib;
import dhrlang.validation.StorageLayouter;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Unified CLI router for all blockchain operations.
 *
 * <p>Routes the following subcommands:
 * <ul>
 *   <li>{@code compile}  — compile @contract to EVM bytecode + ABI</li>
 *   <li>{@code deploy}   — build + sign + send deployment transactions</li>
 *   <li>{@code verify}   — verify contract source on block explorer</li>
 *   <li>{@code gas}      — estimate deployment and call gas costs</li>
 *   <li>{@code fuzz}     — property-fuzz @ensures/@invariant specs for counterexamples</li>
 *   <li>{@code prove}    — statically prove @ensures/@invariant for all inputs (L2b, experimental)</li>
 *   <li>{@code safety}   — unified safety report (audit + fuzzing) with a CI gate</li>
 *   <li>{@code export}   — emit Hardhat/Foundry artifacts + viem/wagmi TS typings</li>
 *   <li>{@code wallet}   — manage keys (create keystore, show address)</li>
 *   <li>{@code networks} — list supported networks and their configs</li>
 *   <li>{@code status}   — check deployment/verification status</li>
 * </ul>
 *
 * <p>This replaces the scattered --compile-evm / --deploy-script flags
 * with a cohesive {@code dhrlang contract <subcommand>} interface.</p>
 */
public final class BlockchainCLI {

    private BlockchainCLI() {}

    // ── Options ──────────────────────────────────────────────────────────

    /**
     * Parsed blockchain CLI options.
     */
    public static final class BlockchainOptions {
        public String subcommand;        // compile | deploy | verify | gas | fuzz | safety | wallet | networks | status
        public String network = "local"; // network name or chain ID
        public String outputDir = "build/evm";
        public String deployFormat = "foundry"; // foundry | ethers
        public String address;           // --address=0x... (for verify)
        public String keystorePath;      // --keystore=<path>
        public String etherscanKey;      // --etherscan-key=<key>
        public boolean dryRun = false;
        public boolean json = false;
        public boolean verbose = false;
        public boolean verify = false;   // --verify (auto-verify after a live deploy)
        public String deployerFrom;      // --from=0x... (deployer address for dry-run prediction)
        // wallet sub-subcommand
        public String walletAction;      // create | show | import
        // fuzz options
        public int fuzzRuns = 256;       // --runs=<n>
        public long fuzzSeed = -1;       // --seed=<n>  (-1 = random)
        // prove options (L2b static prover)
        public int proveBound = 8;       // --bound=<n> (refutation search radius)
        // safety gate options
        public String failOn = "high";   // --fail-on=critical|high|medium|low|none
        public String sourceFile;        // resolved .dhr path (for report locations)
        // export options
        public String exportFormat = "all"; // --format=all|hardhat|foundry|ts
        // stdlib options
        public String stdlibAction;      // list | show | new
        public String stdlibName;        // template name (Ownable, ERC20, ...)
        public String stdlibCustomName;  // --name=<Custom> (rename scaffolded contract)
        // account (ERC-4337) options
        public String accountAction;            // entrypoint | userop
        public String entrypointVersion = "0.6"; // --version=0.6|0.7
        public String accountSender;            // --sender=0x...
        public String accountNonce = "0";       // --nonce=<n|0x..>
        public String accountInitCode = "0x";   // --init-code=0x...
        public String accountCallData = "0x";   // --call-data=0x...
        public String accountPaymasterData = "0x"; // --paymaster-data=0x...
        public String accountCallGas = "0";          // --call-gas=<n>
        public String accountVerificationGas = "0";  // --verification-gas=<n>
        public String accountPreVerificationGas = "0"; // --pre-verification-gas=<n>
        public String accountMaxFee = "0";           // --max-fee=<n>
        public String accountMaxPriorityFee = "0";   // --max-priority-fee=<n>
    }

    // ── Main Entry Point ─────────────────────────────────────────────────

    /**
     * Execute a blockchain CLI subcommand.
     *
     * @param program        the parsed & type-checked program
     * @param sourceCode     original source code
     * @param opts           parsed options
     * @param errorReporter  error reporter
     */
    public static void execute(Program program, String sourceCode,
                                BlockchainOptions opts, ErrorReporter errorReporter) {
        if (opts.subcommand == null) {
            printBlockchainHelp();
            return;
        }

        switch (opts.subcommand) {
            case "compile" -> handleCompile(program, opts, errorReporter);
            case "deploy"  -> handleDeploy(program, sourceCode, opts, errorReporter);
            case "verify"  -> handleVerify(program, sourceCode, opts, errorReporter);
            case "gas"     -> handleGas(program, opts, errorReporter);
            case "fuzz"    -> handleFuzz(program, opts);
            case "prove"   -> handleProve(program, opts);
            case "safety"  -> handleSafety(program, opts);
            case "export"  -> handleExport(program, opts, errorReporter);
            case "stdlib"  -> handleStdlib(opts);
            case "account" -> handleAccount(opts);
            case "wallet"  -> handleWallet(opts);
            case "networks" -> handleNetworks(opts);
            case "status"  -> handleStatus(opts);
            default -> {
                System.err.println("Unknown blockchain subcommand: " + opts.subcommand);
                printBlockchainHelp();
            }
        }
    }

    // ── compile ──────────────────────────────────────────────────────────

    private static void handleCompile(Program program, BlockchainOptions opts,
                                       ErrorReporter errorReporter) {
        var compiler = new EvmContractCompiler(program, errorReporter);
        var artifacts = compiler.compileAll();

        if (artifacts.isEmpty()) {
            System.out.println("No @contract classes found.");
            System.out.println("Hint: Add @contract above your class declaration.");
            return;
        }

        Path outPath = Path.of(opts.outputDir);
        try {
            Files.createDirectories(outPath);

            for (var artifact : artifacts) {
                writeArtifact(artifact, outPath);

                if (opts.verbose) {
                    System.out.println("Compiled: " + artifact.getContractName());
                    System.out.println("  Creation bytecode: " + bytecodeLen(artifact.getCreationBytecode()) + " bytes");
                    System.out.println("  Runtime bytecode:  " + bytecodeLen(artifact.getRuntimeBytecode()) + " bytes");
                    System.out.println("  Gas estimate:      " + artifact.getEstimatedDeployGas());
                }
            }

            System.out.println(artifacts.size() + " contract(s) compiled → " + outPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write artifacts: " + e.getMessage());
            System.exit(2);
        }
    }

    // ── export (Hardhat / Foundry / viem-wagmi interop) ───────────────────

    /**
     * Emit framework-ready interop artifacts for every {@code @contract} in the
     * program: Hardhat ({@code hardhat/<Name>.json}), Foundry
     * ({@code foundry/<Name>.json}) and viem/wagmi TypeScript typings
     * ({@code ts/<Name>.ts} + {@code ts/index.ts} barrel), selected via
     * {@code --format=all|hardhat|foundry|ts}. Purely additive build step — no
     * network access, exits 0 on success.
     */
    private static void handleExport(Program program, BlockchainOptions opts,
                                     ErrorReporter errorReporter) {
        if (program == null) {
            System.err.println("No program to export. Provide a .dhr source file.");
            System.exit(2);
            return;
        }

        String fmt = opts.exportFormat == null ? "all" : opts.exportFormat.toLowerCase(Locale.ROOT);
        boolean doHardhat = fmt.equals("all") || fmt.equals("hardhat");
        boolean doFoundry = fmt.equals("all") || fmt.equals("foundry");
        boolean doTs = fmt.equals("all") || fmt.equals("ts") || fmt.equals("typescript");
        if (!doHardhat && !doFoundry && !doTs) {
            System.err.println("Unknown --format: " + opts.exportFormat
                    + " (expected: all | hardhat | foundry | ts)");
            System.exit(2);
            return;
        }

        var compiler = new EvmContractCompiler(program, errorReporter);
        var artifacts = compiler.compileAll();
        if (artifacts.isEmpty()) {
            System.out.println("No @contract classes found.");
            System.out.println("Hint: Add @contract above your class declaration.");
            return;
        }

        String version = dhrlang.Main.class.getPackage() != null
                ? dhrlang.Main.class.getPackage().getImplementationVersion() : null;
        String sourceName = deriveSourceName(opts.sourceFile);

        Path base = Path.of(opts.outputDir);
        try {
            int written = 0;
            List<String> tsNames = new ArrayList<>();
            for (var artifact : artifacts) {
                String src = sourceName != null ? sourceName
                        : artifact.getContractName() + ".dhr";
                if (doHardhat) {
                    Path dir = base.resolve("hardhat");
                    Files.createDirectories(dir);
                    Files.writeString(dir.resolve(artifact.getContractName() + ".json"),
                            InteropExporter.hardhatArtifact(artifact, src, version));
                    written++;
                }
                if (doFoundry) {
                    Path dir = base.resolve("foundry");
                    Files.createDirectories(dir);
                    Files.writeString(dir.resolve(artifact.getContractName() + ".json"),
                            InteropExporter.foundryArtifact(artifact, src, version));
                    written++;
                }
                if (doTs) {
                    Path dir = base.resolve("ts");
                    Files.createDirectories(dir);
                    Files.writeString(dir.resolve(artifact.getContractName() + ".ts"),
                            InteropExporter.viemModule(artifact, version));
                    tsNames.add(artifact.getContractName());
                    written++;
                }
            }
            if (doTs && !tsNames.isEmpty()) {
                Files.writeString(base.resolve("ts").resolve("index.ts"),
                        InteropExporter.tsBarrel(tsNames, version));
                written++;
            }
            System.out.println(artifacts.size() + " contract(s) exported -> "
                    + written + " file(s) under " + base.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write interop artifacts: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Derive a logical source-file name (basename) from a possibly path-qualified
     * {@code --output}-style argument; returns {@code null} when unset.
     */
    private static String deriveSourceName(String sourceFile) {
        if (sourceFile == null || sourceFile.isEmpty()) return null;
        String s = sourceFile.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    // ── stdlib ───────────────────────────────────────────────────────────

    /**
     * Handle {@code dhrlang contract stdlib <list|show|new>}: browse and scaffold
     * the built-in standard-library base contracts (Ownable, ERC20, ...).
     *
     * <ul>
     *   <li>{@code list} — print the catalog (name + one-line description)</li>
     *   <li>{@code show <Name>} — print the template source to stdout</li>
     *   <li>{@code new <Name> [--name=<Custom>] [--output=<dir>]} — scaffold
     *       {@code <Name>.dhr} (or {@code <Custom>.dhr}) into the output directory</li>
     * </ul>
     */
    private static void handleStdlib(BlockchainOptions opts) {
        String action = opts.stdlibAction == null ? "list" : opts.stdlibAction.toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> stdlibList();
            case "show" -> stdlibShow(opts);
            case "new"  -> stdlibNew(opts);
            default -> {
                System.err.println("Unknown stdlib action: " + opts.stdlibAction
                        + " (expected: list | show | new)");
                System.exit(2);
            }
        }
    }

    private static void stdlibList() {
        System.out.println("DhrLang Standard Library - base contracts\n");
        var catalog = ContractStdlib.catalog();
        int width = catalog.keySet().stream().mapToInt(String::length).max().orElse(18);
        for (var entry : catalog.entrySet()) {
            System.out.printf("  %-" + width + "s  %s%n", entry.getKey(), entry.getValue());
        }
        System.out.println();
        System.out.println("Show source:  dhrlang contract stdlib show <Name>");
        System.out.println("Scaffold:     dhrlang contract stdlib new <Name> [--name=<Custom>] [--output=<dir>]");
    }

    private static void stdlibShow(BlockchainOptions opts) {
        if (opts.stdlibName == null) {
            System.err.println("Missing template name. Usage: dhrlang contract stdlib show <Name>");
            System.err.println("Available: " + String.join(", ", ContractStdlib.availableContracts()));
            System.exit(2);
            return;
        }
        String source = ContractStdlib.getByName(opts.stdlibName);
        if (source == null) {
            System.err.println("Unknown stdlib contract: " + opts.stdlibName);
            System.err.println("Available: " + String.join(", ", ContractStdlib.availableContracts()));
            System.exit(2);
            return;
        }
        System.out.println(source);
    }

    private static void stdlibNew(BlockchainOptions opts) {
        if (opts.stdlibName == null) {
            System.err.println("Missing template name. Usage: dhrlang contract stdlib new <Name> [--name=<Custom>]");
            System.err.println("Available: " + String.join(", ", ContractStdlib.availableContracts()));
            System.exit(2);
            return;
        }
        String source = ContractStdlib.getByName(opts.stdlibName);
        if (source == null) {
            System.err.println("Unknown stdlib contract: " + opts.stdlibName);
            System.err.println("Available: " + String.join(", ", ContractStdlib.availableContracts()));
            System.exit(2);
            return;
        }

        String outName = opts.stdlibName;
        if (opts.stdlibCustomName != null && !opts.stdlibCustomName.isBlank()) {
            outName = opts.stdlibCustomName.trim();
            // Rename the scaffolded contract's class to the custom name.
            source = source.replaceFirst("class\\s+" + java.util.regex.Pattern.quote(opts.stdlibName) + "\\b",
                    "class " + java.util.regex.Matcher.quoteReplacement(outName));
        }

        Path base = Path.of(opts.outputDir);
        Path target = base.resolve(outName + ".dhr");
        try {
            Files.createDirectories(base);
            if (Files.exists(target)) {
                System.err.println("Refusing to overwrite existing file: " + target.toAbsolutePath());
                System.err.println("Use --output=<dir> or --name=<Custom> to choose a different path.");
                System.exit(2);
                return;
            }
            Files.writeString(target, source);
            System.out.println("Scaffolded " + opts.stdlibName + " -> " + target.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write scaffold: " + e.getMessage());
            System.exit(2);
        }
    }

    // ── deploy ───────────────────────────────────────────────────────────

    private static void handleDeploy(Program program, String sourceCode,
                                      BlockchainOptions opts, ErrorReporter errorReporter) {
        // 1. Compile
        var compiler = new EvmContractCompiler(program, errorReporter);
        var artifacts = compiler.compileAll();
        if (artifacts.isEmpty()) {
            System.err.println("No @contract classes found to deploy.");
            return;
        }

        // 2. Resolve network
        L2ChainConfig chain = resolveChain(opts.network);
        if (chain == null) {
            System.err.println("Unknown network: " + opts.network);
            System.err.println("Use --network=<name> (run 'dhrlang contract networks' to see options).");
            return;
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              DhrLang Contract Deployment                     ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("  Network:    " + chain.getName() + " (chainId: " + chain.getChainId() + ")");
        System.out.println("  Contracts:  " + artifacts.size());

        // 3. Gas estimation
        for (var artifact : artifacts) {
            var breakdown = GasEstimator.estimateDeployGas(
                    artifact.getCreationBytecode(),
                    artifact.getRuntimeBytecode(),
                    getStorageSlotCount(program, artifact.getContractName())
            );
            System.out.println("  " + artifact.getContractName() + ": ~" + breakdown.getTotalEstimate() + " gas");
        }

        if (opts.dryRun) {
            System.out.println("\n  [DRY RUN] - No transactions will be sent.");

            String dryFrom = resolveDeployerForDryRun(opts, chain);
            System.out.println("  Deployer:   " + dryFrom + " (assumed; override with --from=0x..)");

            // Predict the CREATE address of each contract (depends only on sender+nonce).
            BroadcastArtifact broadcast = new BroadcastArtifact(Long.parseLong(chain.getChainId()));
            long dryNonce = 0;
            for (var artifact : artifacts) {
                var breakdown = GasEstimator.estimateDeployGas(
                        artifact.getCreationBytecode(),
                        artifact.getRuntimeBytecode(),
                        getStorageSlotCount(program, artifact.getContractName()));
                String predicted = WalletManager.computeCreateAddress(dryFrom, dryNonce);
                broadcast.addPlanned(artifact.getContractName(), dryFrom, dryNonce,
                        breakdown.getTotalEstimate(), artifact.getCreationBytecodeHex(), predicted);
                System.out.println("    " + artifact.getContractName()
                        + " -> " + predicted + " (predicted, nonce " + dryNonce + ")");
                dryNonce++;
            }
            if (opts.verify) {
                System.out.println("  --verify: contracts would be source-verified on "
                        + chain.getName() + " after broadcast.");
            }
            System.out.println("╚══════════════════════════════════════════════════════════════╝");

            // Write artifacts + deploy script + Foundry-style broadcast artifact
            try {
                Path outPath = Path.of(opts.outputDir);
                Files.createDirectories(outPath);
                for (var artifact : artifacts) {
                    writeArtifact(artifact, outPath);
                }

                var deployer = new DeploymentManager().setTargetChain(chain);
                String script = "ethers".equals(opts.deployFormat)
                        ? deployer.generateEthersScript(artifacts)
                        : deployer.generateFoundryScript(artifacts);
                String ext = "ethers".equals(opts.deployFormat) ? ".deploy.js" : ".deploy.sol";
                Files.writeString(outPath.resolve("Deploy" + ext), script);

                Path bcPath = writeBroadcast(broadcast, outPath, true);
                System.out.println("\nArtifacts and deploy script written to: " + outPath.toAbsolutePath());
                System.out.println("Broadcast artifact (Foundry-compatible): " + bcPath);
            } catch (IOException e) {
                System.err.println("Failed to write artifacts: " + e.getMessage());
            }
            return;
        }

        // 4. Load wallet
        WalletManager wallet = new WalletManager();
        try {
            wallet.autoLoad();
        } catch (WalletManager.WalletException e) {
            System.err.println("Wallet error: " + e.getMessage());
            System.exit(2);
            return;
        }

        System.out.println("  Deployer:   " + wallet.getAddress());
        System.out.println("  Key source: " + wallet.getKeySource());

        // 5. Build and sign transactions
        var deployer = new DeploymentManager()
                .setTargetChain(chain)
                .setDeployerAddress(wallet.getAddress());

        // Try to connect to RPC for live deployment
        String rpcUrl = resolveRpcUrl(chain);
        EthJsonRpcClient rpcClient = null;
        if (rpcUrl != null) {
            try {
                rpcClient = new EthJsonRpcClient(rpcUrl);
                long remoteChainId = rpcClient.getChainId();
                long expectedChainId = Long.parseLong(chain.getChainId());
                if (remoteChainId != expectedChainId) {
                    System.err.println("  WARNING: Chain ID mismatch! Expected " + expectedChainId
                            + " but RPC returned " + remoteChainId);
                    rpcClient = null;
                } else {
                    // Fetch live nonce
                    long nonce = rpcClient.getTransactionCount(wallet.getAddress());
                    deployer.setNonce(nonce);
                    System.out.println("  RPC:        " + rpcUrl);
                    System.out.println("  Nonce:      " + nonce);

                    // Fetch live gas price
                    long gasPrice = rpcClient.getGasPrice();
                    deployer.setMaxFeePerGas(gasPrice * 2); // 2x current for safety
                    deployer.setMaxPriorityFeePerGas(gasPrice / 10);
                    System.out.println("  Gas price:  " + (gasPrice / 1_000_000_000L) + " gwei");
                }
            } catch (Exception e) {
                System.out.println("  RPC unavailable (" + e.getMessage() + ") — generating offline tx");
                rpcClient = null;
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        BroadcastArtifact broadcast = new BroadcastArtifact(Long.parseLong(chain.getChainId()));
        int verifiedCount = 0;

        for (var artifact : artifacts) {
            var tx = deployer.buildDeployTx(artifact);
            System.out.println("  Building tx for " + artifact.getContractName() + "...");

            String signedTx = wallet.signDeployTx(tx);
            System.out.println("  Signed: " + signedTx.substring(0, Math.min(20, signedTx.length())) + "...");

            String predicted = WalletManager.computeCreateAddress(wallet.getAddress(), tx.getNonce());
            boolean recorded = false;

            // If we have an RPC client, broadcast live
            if (rpcClient != null) {
                try {
                    System.out.println("  Broadcasting to " + chain.getName() + "...");
                    var result = rpcClient.deploy(signedTx);
                    System.out.println("  ✓ Deployed!");
                    System.out.println("    Contract: " + result.contractAddress);
                    System.out.println("    Tx hash:  " + result.txHash);
                    System.out.println("    Gas used: " + result.gasUsed);
                    System.out.println("    Block:    " + result.blockNumber);
                    if (chain.getExplorerUrl() != null) {
                        System.out.println("    Explorer: " + chain.getExplorerUrl()
                                + "/address/" + result.contractAddress);
                    }

                    deployer.recordDeployment(artifact.getContractName(),
                            result.contractAddress, result.txHash,
                            result.blockNumber, result.gasUsed);
                    broadcast.addBroadcast(artifact.getContractName(), wallet.getAddress(),
                            tx.getNonce(), tx.getGasLimit(), tx.getCreationBytecodeHex(),
                            result.contractAddress, result.txHash, result.gasUsed, result.blockNumber);
                    recorded = true;

                    // Auto-verify: the one-command verified-deploy loop.
                    if (opts.verify) {
                        if (runVerification(artifact, result.contractAddress, chain, opts, sourceCode)) {
                            deployer.markVerified(artifact.getContractName());
                            verifiedCount++;
                        }
                    }
                } catch (EthJsonRpcClient.RpcException e) {
                    System.err.println("  ✗ Deployment failed: " + e.getMessage());
                    System.err.println("    Signed tx saved to disk for manual broadcast.");
                }
            } else {
                System.out.println("  Predicted address: " + predicted + " (nonce " + tx.getNonce() + ")");
                System.out.println("  Raw signed tx written to " + opts.outputDir + "/"
                        + artifact.getContractName() + ".signed.tx");
            }

            // Offline / failed broadcasts still get a planned entry (predicted address, no hash).
            if (!recorded) {
                broadcast.addPlanned(artifact.getContractName(), wallet.getAddress(),
                        tx.getNonce(), tx.getGasLimit(), tx.getCreationBytecodeHex(), predicted);
            }

            try {
                Path outPath = Path.of(opts.outputDir);
                Files.createDirectories(outPath);
                writeArtifact(artifact, outPath);
                Files.writeString(outPath.resolve(artifact.getContractName() + ".signed.tx"), signedTx);
            } catch (IOException e) {
                System.err.println("Failed to write: " + e.getMessage());
            }
        }

        // Write the Foundry-compatible broadcast artifact (run-latest.json).
        String bcDisplay = null;
        try {
            Path outPath = Path.of(opts.outputDir);
            Files.createDirectories(outPath);
            bcDisplay = writeBroadcast(broadcast, outPath, false).toString();
        } catch (IOException e) {
            System.err.println("Failed to write broadcast artifact: " + e.getMessage());
        }

        wallet.clear();

        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        if (bcDisplay != null) {
            System.out.println("  Broadcast artifact: " + bcDisplay);
        }
        if (opts.verify && rpcClient != null) {
            System.out.println("  Verified: " + verifiedCount + "/" + artifacts.size() + " contract(s)");
        }
        System.out.println("  Next steps:");
        if (rpcClient == null) {
            System.out.println("    1. Broadcast: cast send --raw <signed.tx> --rpc-url " + chain.getRpcUrlTemplate());
            System.out.println("    2. Verify:    dhrlang contract deploy --verify --network=" + opts.network + " contract.dhr");
        } else if (!opts.verify) {
            System.out.println("    1. Verify:    dhrlang contract verify --address=<deployed> --network=" + opts.network + " contract.dhr");
            System.out.println("       (tip: pass --verify to deploy and verify in one command)");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    // ── verify ───────────────────────────────────────────────────────────

    private static void handleVerify(Program program, String sourceCode,
                                      BlockchainOptions opts, ErrorReporter errorReporter) {
        if (opts.address == null || opts.address.isBlank()) {
            System.err.println("Missing --address=<contract_address>.");
            System.err.println("Usage: dhrlang contract verify --address=0x... --network=sepolia contract.dhr");
            return;
        }

        L2ChainConfig chain = resolveChain(opts.network);
        if (chain == null) {
            System.err.println("Unknown network: " + opts.network);
            return;
        }

        var compiler = new EvmContractCompiler(program, errorReporter);
        var artifacts = compiler.compileAll();
        if (artifacts.isEmpty()) {
            System.err.println("No @contract classes found to verify.");
            return;
        }

        var verifier = new ContractVerifier()
                .setChain(chain)
                .setDryRun(opts.dryRun);

        if (opts.etherscanKey != null) {
            verifier.setApiKey(opts.etherscanKey);
        }

        // Verify each contract (or just the first one matching the address)
        for (var artifact : artifacts) {
            System.out.println("Verifying " + artifact.getContractName() + " at " + opts.address
                    + " on " + chain.getName() + "...");

            var result = verifier.verify(artifact, opts.address, sourceCode);
            if (result.isSuccess()) {
                System.out.println("  ✓ " + result.getMessage());
                if (result.getExplorerUrl() != null) {
                    System.out.println("  View: " + result.getExplorerUrl());
                }
            } else {
                System.err.println("  ✗ " + result.getMessage());
            }

            // Also generate manual verify commands
            String verifyCmd = verifier.generateVerifyCommand(artifact, opts.address);
            try {
                Path outPath = Path.of(opts.outputDir);
                Files.createDirectories(outPath);
                Files.writeString(outPath.resolve("verify-" + artifact.getContractName() + ".sh"), verifyCmd);
            } catch (IOException e) {
                // Non-fatal
            }
        }
    }

    // ── gas ──────────────────────────────────────────────────────────────

    private static void handleGas(Program program, BlockchainOptions opts,
                                   ErrorReporter errorReporter) {
        var compiler = new EvmContractCompiler(program, errorReporter);
        var artifacts = compiler.compileAll();
        if (artifacts.isEmpty()) {
            System.err.println("No @contract classes found for gas estimation.");
            return;
        }

        for (var artifact : artifacts) {
            int slots = getStorageSlotCount(program, artifact.getContractName());
            var breakdown = GasEstimator.estimateDeployGas(
                    artifact.getCreationBytecode(),
                    artifact.getRuntimeBytecode(),
                    slots
            );

            if (opts.json) {
                System.out.println(GasEstimator.formatJson(breakdown));
            } else {
                System.out.println("Contract: " + artifact.getContractName());
                System.out.println(GasEstimator.formatReport(breakdown));

                // Also show L2 estimates
                L2ChainConfig chain = resolveChain(opts.network);
                if (chain != null && chain.getChainType() != L2ChainConfig.ChainType.L1
                        && chain.getChainType() != L2ChainConfig.ChainType.LOCAL) {
                    System.out.println("L2 Cost Estimates (" + chain.getName() + "):");
                    for (var entry : GasEstimator.estimateL2Costs(breakdown, chain).entrySet()) {
                        System.out.printf("  %-30s %12.8f ETH%n", entry.getKey() + ":", entry.getValue().getCostEth());
                    }
                    System.out.println();
                }
            }
        }
    }

    // ── fuzz ─────────────────────────────────────────────────────────────

    /**
     * Property-fuzz every contract's specifications (provable-safety level L3).
     * Generates random inputs, executes each function over a simulated EVM state,
     * and reports any {@code @ensures}/{@code @invariant} counterexamples. Exits
     * non-zero when a violation is found, so it doubles as a CI gate.
     */
    private static void handleFuzz(Program program, BlockchainOptions opts) {
        if (program == null) {
            System.err.println("No program to fuzz. Provide a .dhr source file.");
            System.exit(2);
            return;
        }

        var fuzzer = new dhrlang.testing.ContractFuzzer(program);
        if (opts.fuzzRuns > 0) {
            fuzzer.setRuns(opts.fuzzRuns);
        }
        if (opts.fuzzSeed >= 0) {
            fuzzer.setSeed(opts.fuzzSeed);
        }

        fuzzer.fuzzAll();
        System.out.println(fuzzer.formatReport());

        if (fuzzer.hasFailures()) {
            System.exit(1);
        }
    }

    // ── prove ────────────────────────────────────────────────────────────

    /**
     * Statically prove design-by-contract specifications for <em>all</em> inputs
     * (provable-safety level L2b, experimental).
     *
     * <p>Where {@code fuzz} samples inputs hunting for a counterexample, this
     * attempts a universal proof of each {@code @ensures} postcondition and
     * {@code @invariant} via symbolic execution plus a hand-rolled
     * Fourier–Motzkin decision procedure for linear integer arithmetic. Every
     * obligation is reported as {@code PROVED}, {@code REFUTED} (with a concrete
     * counterexample cross-checked by the L3 {@link dhrlang.testing.SpecFuzzEngine}),
     * or {@code UNKNOWN} (with a reason). Proving is sound only under checked
     * arithmetic, so it is enabled for {@code @checked} functions; otherwise the
     * obligation is {@code UNKNOWN} (refutation still runs). Exits non-zero when
     * any obligation is refuted, so it doubles as a CI gate.
     */
    private static void handleProve(Program program, BlockchainOptions opts) {
        if (program == null) {
            System.err.println("No program to prove. Provide a .dhr source file.");
            System.exit(2);
            return;
        }

        var prover = new SpecProver(program).setBound(opts.proveBound);
        prover.proveAll();

        if (opts.json) {
            System.out.println(prover.formatJson());
        } else {
            System.out.println(prover.formatReport());
        }

        if (prover.hasRefutations()) {
            System.exit(1);
        }
    }

    // ── safety ───────────────────────────────────────────────────────────

    /**
     * Generate a unified safety report (provable-safety level L4) and gate CI.
     *
     * <p>Composes the static audit (ContractValidator + L1 SecurityAnalyzer +
     * arithmetic/invariant analyzers) with an L3 spec-fuzzing pass, then emits a
     * safety score, a human-readable Markdown report (stdout, or JSON with
     * {@code --json}), and SARIF + Markdown artifacts for CI ingestion. Exits
     * non-zero when any finding meets the {@code --fail-on} severity threshold
     * (default {@code high}), so it doubles as a release gate.
     */
    private static void handleSafety(Program program, BlockchainOptions opts) {
        if (program == null) {
            System.err.println("No program to analyze. Provide a .dhr source file.");
            System.exit(2);
            return;
        }

        var auditor = new AuditReportGenerator();
        auditor.setProjectName(opts.sourceFile != null ? opts.sourceFile : "DhrLang Project");
        String version = dhrlang.Main.class.getPackage() != null
                ? dhrlang.Main.class.getPackage().getImplementationVersion() : null;
        auditor.setCompilerVersion(version != null ? version : "(development)");
        auditor.enableSpecFuzzing(opts.fuzzRuns, opts.fuzzSeed >= 0 ? opts.fuzzSeed : 0L);

        var report = auditor.analyze(program);

        // Human / machine readable report to stdout
        if (opts.json) {
            System.out.println(AuditReportGenerator.formatJson(report));
        } else {
            System.out.println(AuditReportGenerator.formatMarkdown(report));
        }

        // Persist SARIF + Markdown artifacts for CI ingestion (Code Scanning, job summary)
        try {
            Path outPath = Path.of(opts.outputDir);
            Files.createDirectories(outPath);
            String sarif = dhrlang.production.SarifFormatter.format(report, opts.sourceFile);
            Files.writeString(outPath.resolve("safety.sarif"), sarif);
            Files.writeString(outPath.resolve("safety-report.md"),
                    AuditReportGenerator.formatMarkdown(report));
            System.err.println("Wrote " + outPath.resolve("safety.sarif")
                    + " and " + outPath.resolve("safety-report.md"));
        } catch (IOException e) {
            System.err.println("Warning: could not write safety artifacts: " + e.getMessage());
        }

        // CI gate
        int gate = severityRank(opts.failOn);
        long blocking = gate < 0 ? 0 : report.getFindings().stream()
                .filter(f -> f.getSeverity().getWeight() >= gate)
                .count();
        String scoreLine = "safety score " + report.getSafetyScore()
                + "/100, grade " + report.getSafetyGrade();
        if (blocking > 0) {
            System.err.println();
            System.err.println("Safety gate FAILED: " + blocking + " finding(s) at or above "
                    + opts.failOn.toUpperCase(Locale.ROOT) + " (" + scoreLine + ").");
            System.exit(1);
        }
        System.err.println("Safety gate passed (" + scoreLine + ").");
    }

    /**
     * Map a {@code --fail-on} keyword to a {@link AuditReportGenerator.Severity}
     * weight. Returns {@code -1} for {@code none}/{@code off} (gate disabled).
     */
    private static int severityRank(String failOn) {
        if (failOn == null) return AuditReportGenerator.Severity.HIGH.getWeight();
        return switch (failOn.toLowerCase(Locale.ROOT)) {
            case "critical" -> AuditReportGenerator.Severity.CRITICAL.getWeight();
            case "high"     -> AuditReportGenerator.Severity.HIGH.getWeight();
            case "medium"   -> AuditReportGenerator.Severity.MEDIUM.getWeight();
            case "low"      -> AuditReportGenerator.Severity.LOW.getWeight();
            case "none", "off" -> -1;
            default         -> AuditReportGenerator.Severity.HIGH.getWeight();
        };
    }

    // ── wallet ───────────────────────────────────────────────────────────

    private static void handleWallet(BlockchainOptions opts) {
        String action = opts.walletAction != null ? opts.walletAction : "show";

        switch (action) {
            case "create" -> {
                System.out.println("Creating encrypted keystore...");
                java.io.Console console = System.console();
                if (console == null) {
                    System.err.println("Interactive console required. Cannot create keystore in piped mode.");
                    return;
                }

                char[] key = console.readPassword("Enter private key (hex, 0x...): ");
                if (key == null || key.length == 0) { System.err.println("No key entered."); return; }

                char[] pass1 = console.readPassword("Enter keystore password: ");
                char[] pass2 = console.readPassword("Confirm password: ");
                if (!Arrays.equals(pass1, pass2)) {
                    System.err.println("Passwords do not match.");
                    return;
                }

                Path path = opts.keystorePath != null ? Path.of(opts.keystorePath) : null;
                try {
                    WalletManager.createKeystore(path, new String(key), new String(pass1));
                    System.out.println("Keystore created successfully.");
                    if (path == null) {
                        System.out.println("Location: ~/.dhrlang/keystore.enc");
                    } else {
                        System.out.println("Location: " + path);
                    }
                } catch (WalletManager.WalletException e) {
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    java.util.Arrays.fill(key, '\0');
                    java.util.Arrays.fill(pass1, '\0');
                    java.util.Arrays.fill(pass2, '\0');
                }
            }
            case "show" -> {
                WalletManager wallet = new WalletManager();
                try {
                    wallet.autoLoad();
                    System.out.println("Address:    " + wallet.getAddress());
                    System.out.println("Key source: " + wallet.getKeySource());
                } catch (WalletManager.WalletException e) {
                    System.err.println("No wallet configured: " + e.getMessage());
                    System.err.println("\nSetup options:");
                    System.err.println("  1. Set env:          export DHRLANG_PRIVATE_KEY=0x...");
                    System.err.println("  2. Create keystore:  dhrlang contract wallet create");
                } finally {
                    wallet.clear();
                }
            }
            default -> {
                System.err.println("Unknown wallet action: " + action);
                System.err.println("Usage: dhrlang contract wallet [create|show]");
            }
        }
    }

    // ── account (ERC-4337) ───────────────────────────────────────────────

    private static void handleAccount(BlockchainOptions opts) {
        String action = opts.accountAction;
        if (action == null) {
            System.err.println("Missing account action.");
            System.err.println("Usage: dhrlang contract account [entrypoint|userop] [options]");
            System.exit(2);
            return;
        }
        switch (action) {
            case "entrypoint" -> handleAccountEntrypoint(opts);
            case "userop"     -> handleAccountUserOp(opts);
            default -> {
                System.err.println("Unknown account action: " + action);
                System.err.println("Usage: dhrlang contract account [entrypoint|userop] [options]");
                System.exit(2);
            }
        }
    }

    private static void handleAccountEntrypoint(BlockchainOptions opts) {
        String version = opts.entrypointVersion;
        String addr = AccountAbstraction.entryPointFor(version);
        if (addr == null) {
            System.err.println("Unknown EntryPoint version: " + version + " (supported: 0.6, 0.7)");
            System.exit(2);
            return;
        }
        String label = AccountAbstraction.versionLabel(addr);
        if (opts.json) {
            System.out.println("{\n  \"version\": \"" + label + "\",\n  \"entryPoint\": \"" + addr + "\"\n}");
            return;
        }
        System.out.println("ERC-4337 EntryPoint v" + label);
        System.out.println("  Address: " + addr);
        System.out.println("  Note:    Identical on every EVM chain (deployed via CREATE2).");
    }

    private static void handleAccountUserOp(BlockchainOptions opts) {
        if (opts.accountSender == null || opts.accountSender.isBlank()) {
            System.err.println("Missing --sender=0x... for account userop.");
            System.err.println("Usage: dhrlang contract account userop --sender=0x.. [--nonce=N] "
                    + "[--call-data=0x..] [--network=sepolia]");
            System.exit(2);
            return;
        }
        String entryPoint = AccountAbstraction.entryPointFor(opts.entrypointVersion);
        if (entryPoint == null) {
            System.err.println("Unknown EntryPoint version: " + opts.entrypointVersion + " (supported: 0.6, 0.7)");
            System.exit(2);
            return;
        }
        if (!AccountAbstraction.ENTRYPOINT_V0_6.equals(entryPoint)) {
            System.err.println("account userop currently computes the EntryPoint v0.6 userOpHash only.");
            System.err.println("(v0.7 repacks the UserOperation differently - use --version=0.6 or omit it.)");
            System.exit(2);
            return;
        }
        L2ChainConfig chain = resolveChain(opts.network);
        if (chain == null) {
            System.err.println("Unknown network: " + opts.network);
            System.exit(2);
            return;
        }
        BigInteger chainId;
        try {
            chainId = new BigInteger(chain.getChainId());
        } catch (NumberFormatException e) {
            System.err.println("Network has a non-numeric chainId: " + chain.getChainId());
            System.exit(2);
            return;
        }

        AccountAbstraction.UserOperation op;
        try {
            op = new AccountAbstraction.UserOperation()
                    .sender(opts.accountSender)
                    .nonce(AccountAbstraction.parseUint(opts.accountNonce))
                    .initCode(AccountAbstraction.parseBytes(opts.accountInitCode))
                    .callData(AccountAbstraction.parseBytes(opts.accountCallData))
                    .callGasLimit(AccountAbstraction.parseUint(opts.accountCallGas))
                    .verificationGasLimit(AccountAbstraction.parseUint(opts.accountVerificationGas))
                    .preVerificationGas(AccountAbstraction.parseUint(opts.accountPreVerificationGas))
                    .maxFeePerGas(AccountAbstraction.parseUint(opts.accountMaxFee))
                    .maxPriorityFeePerGas(AccountAbstraction.parseUint(opts.accountMaxPriorityFee))
                    .paymasterAndData(AccountAbstraction.parseBytes(opts.accountPaymasterData));
        } catch (IllegalArgumentException | ArithmeticException e) {
            System.err.println("Invalid UserOperation field: " + e.getMessage());
            System.exit(2);
            return;
        }

        String userOpHash = AccountAbstraction.userOpHashHex(op, entryPoint, chainId);
        String json = AccountAbstraction.toJson(op);

        if (opts.json) {
            System.out.println("{\n"
                    + "  \"entryPoint\": \"" + entryPoint + "\",\n"
                    + "  \"chainId\": " + chainId + ",\n"
                    + "  \"network\": \"" + chain.getName() + "\",\n"
                    + "  \"userOpHash\": \"" + userOpHash + "\",\n"
                    + "  \"userOp\": " + json.replace("\n", "\n  ") + "\n"
                    + "}");
            return;
        }

        System.out.println("ERC-4337 UserOperation (v0.6)");
        System.out.println("  Network:    " + chain.getName() + " (chainId " + chainId + ")");
        System.out.println("  EntryPoint: " + entryPoint);
        System.out.println();
        System.out.println(json);
        System.out.println();
        System.out.println("  userOpHash: " + userOpHash);
        System.out.println("  (Canonical EntryPoint.getUserOpHash - the value the account owner signs.)");
    }

    // ── networks ─────────────────────────────────────────────────────────

    private static void handleNetworks(BlockchainOptions opts) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                  Supported Networks                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        var chains = L2ChainConfig.allChains();
        for (var chain : chains) {
            System.out.printf("  %-22s chainId=%-10s type=%-12s token=%s%n",
                    chain.getName(), chain.getChainId(), chain.getChainType(), chain.getNativeToken());
        }

        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("  Use --network=<name> to target a specific chain.");
        System.out.println("  Example: dhrlang contract deploy --network=sepolia contract.dhr");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    // ── status ───────────────────────────────────────────────────────────

    private static void handleStatus(BlockchainOptions opts) {
        if (opts.address == null) {
            System.err.println("Missing --address=<contract_address>.");
            System.err.println("Usage: dhrlang contract status --address=0x... --network=sepolia");
            return;
        }

        L2ChainConfig chain = resolveChain(opts.network);
        if (chain == null) {
            System.err.println("Unknown network: " + opts.network);
            return;
        }

        System.out.println("Contract: " + opts.address);
        System.out.println("Network:  " + chain.getName());
        String url = chain.getExplorerUrl();
        if (url != null) {
            System.out.println("Explorer: " + url + "/address/" + opts.address);
        }
    }

    // ── Help ─────────────────────────────────────────────────────────────

    static void printBlockchainHelp() {
        System.out.println("DhrLang Blockchain Tools\n");
        System.out.println("Usage: java -jar DhrLang.jar contract <subcommand> [options] <file.dhr>\n");
        System.out.println("Subcommands:");
        System.out.println("  compile    Compile @contract classes to EVM bytecode + ABI");
        System.out.println("  deploy     Build, sign, and deploy contracts to a network");
        System.out.println("  verify     Verify contract source on block explorer (Etherscan)");
        System.out.println("  gas        Estimate deployment and function call gas costs");
        System.out.println("  fuzz       Property-fuzz @ensures/@invariant specs for counterexamples");
        System.out.println("  prove      Statically prove @ensures/@invariant for all inputs (L2b, experimental)");
        System.out.println("  safety     Unified safety report (audit + fuzzing) with a CI gate");
        System.out.println("  export     Emit Hardhat/Foundry artifacts + viem/wagmi TS typings");
        System.out.println("  stdlib     Browse & scaffold standard base contracts (Ownable, ERC20, ...)");
        System.out.println("  account    ERC-4337 account abstraction: EntryPoint + offline userOpHash");
        System.out.println("  wallet     Manage wallet keys (create keystore, show address)");
        System.out.println("  networks   List supported blockchain networks");
        System.out.println("  status     Check contract deployment status");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --network=<name>        Target network (default: local)");
        System.out.println("                          Examples: mainnet, sepolia, arbitrum, base, optimism, local");
        System.out.println("  --output=<dir>          Output directory (default: build/evm/)");
        System.out.println("  --address=<0x...>       Contract address (for verify/status)");
        System.out.println("  --deploy-format=<fmt>   Deploy script format: foundry | ethers");
        System.out.println("  --etherscan-key=<key>   Etherscan API key (or set DHRLANG_ETHERSCAN_API_KEY)");
        System.out.println("  --keystore=<path>       Path to encrypted keystore file");
        System.out.println("  --runs=<n>              Fuzz iterations per function (default: 256)");
        System.out.println("  --seed=<n>              Fuzz RNG seed for reproducible runs");
        System.out.println("  --bound=<n>             Prove refutation search radius per param (default: 8)");
        System.out.println("  --fail-on=<severity>    Safety gate threshold: critical|high|medium|low|none (default: high)");
        System.out.println("  --format=<fmt>          Export format: all|hardhat|foundry|ts (default: all)");
        System.out.println("  --name=<Custom>         Rename a scaffolded stdlib contract (stdlib new)");
        System.out.println("  --version=<0.6|0.7>     ERC-4337 EntryPoint version (account; default: 0.6)");
        System.out.println("  --sender=<0x...>        Smart-account sender address (account userop)");
        System.out.println("  --nonce=<n>             UserOperation nonce (account userop; default: 0)");
        System.out.println("  --call-data=<0x...>     UserOperation callData (account userop; default: 0x)");
        System.out.println("  --verify                Auto-verify each contract after a live deploy");
        System.out.println("  --from=<0x...>          Deployer address for dry-run address prediction");
        System.out.println("  --dry-run               Simulate without sending transactions");
        System.out.println("  --json                  Output in JSON format");
        System.out.println("  --verbose               Show detailed output");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  dhrlang contract compile token.dhr");
        System.out.println("  dhrlang contract gas token.dhr");
        System.out.println("  dhrlang contract fuzz --runs=512 --seed=42 token.dhr");
        System.out.println("  dhrlang contract prove --bound=8 token.dhr");
        System.out.println("  dhrlang contract safety --fail-on=high token.dhr");
        System.out.println("  dhrlang contract export --format=all token.dhr");
        System.out.println("  dhrlang contract stdlib list");
        System.out.println("  dhrlang contract stdlib show ERC20");
        System.out.println("  dhrlang contract stdlib new Ownable --name=MyToken --output=input/contracts");
        System.out.println("  dhrlang contract account entrypoint --version=0.6");
        System.out.println("  dhrlang contract account userop --sender=0xabc... --nonce=1 --network=base");
        System.out.println("  dhrlang contract deploy --network=sepolia --dry-run token.dhr");
        System.out.println("  dhrlang contract deploy --network=local token.dhr");
        System.out.println("  dhrlang contract deploy --network=sepolia --verify token.dhr");
        System.out.println("  dhrlang contract verify --address=0x... --network=sepolia token.dhr");
        System.out.println("  dhrlang contract wallet create");
        System.out.println("  dhrlang contract networks");
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private static void writeArtifact(ContractArtifact artifact, Path outPath) throws IOException {
        String name = artifact.getContractName();
        byte[] creation = artifact.getCreationBytecode();
        byte[] runtime = artifact.getRuntimeBytecode();
        String abi = artifact.getAbiJson();

        if (creation != null && creation.length > 0)
            Files.write(outPath.resolve(name + ".bin"), creation);
        if (runtime != null && runtime.length > 0)
            Files.write(outPath.resolve(name + ".runtime.bin"), runtime);
        if (abi != null && !abi.isEmpty())
            Files.writeString(outPath.resolve(name + ".abi.json"), abi);
    }

    /** The canonical first Anvil/Hardhat dev account (deterministic mnemonic, index 0). */
    private static final String ANVIL_ACCOUNT_0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

    /**
     * Resolve the deployer address used to predict CREATE addresses in a dry run.
     * Priority: {@code --from} -> {@code DHRLANG_DEPLOYER} env -> Anvil account #0 for
     * local chains -> the zero address.
     */
    private static String resolveDeployerForDryRun(BlockchainOptions opts, L2ChainConfig chain) {
        if (opts.deployerFrom != null && !opts.deployerFrom.isBlank()) {
            return opts.deployerFrom.trim();
        }
        String env = System.getenv("DHRLANG_DEPLOYER");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (isLocalChain(chain)) {
            return ANVIL_ACCOUNT_0;
        }
        return "0x0000000000000000000000000000000000000000";
    }

    /**
     * Write a Foundry-compatible broadcast artifact under {@code outPath} and return the
     * file that was written (e.g. {@code <out>/broadcast/Deploy.s.sol/31337/run-latest.json}).
     */
    private static Path writeBroadcast(BroadcastArtifact broadcast, Path outPath, boolean dryRun)
            throws IOException {
        Path bcPath = outPath.resolve(broadcast.relativePath(dryRun));
        Files.createDirectories(bcPath.getParent());
        Files.writeString(bcPath, broadcast.toJson());
        return bcPath;
    }

    /**
     * Run source verification for a single deployed contract, printing the outcome.
     *
     * @return {@code true} if verification succeeded (or was a successful dry run)
     */
    private static boolean runVerification(ContractArtifact artifact, String address,
                                           L2ChainConfig chain, BlockchainOptions opts,
                                           String sourceCode) {
        var verifier = new ContractVerifier()
                .setChain(chain)
                .setDryRun(opts.dryRun);
        if (opts.etherscanKey != null) {
            verifier.setApiKey(opts.etherscanKey);
        }
        System.out.println("  Verifying " + artifact.getContractName() + " at " + address
                + " on " + chain.getName() + "...");
        var result = verifier.verify(artifact, address, sourceCode);
        if (result.isSuccess()) {
            System.out.println("    verified: " + result.getMessage());
            if (result.getExplorerUrl() != null) {
                System.out.println("    view: " + result.getExplorerUrl());
            }
            return true;
        }
        System.err.println("    not verified: " + result.getMessage());
        return false;
    }

    private static int bytecodeLen(byte[] bc) {
        return bc != null ? bc.length : 0;
    }

    static L2ChainConfig resolveChain(String name) {
        if (name == null) return L2ChainConfig.LOCAL_ANVIL;
        return switch (name.toLowerCase().replace("-", "").replace("_", "").replace(" ", "")) {
            case "mainnet", "ethereum", "ethereummainnet", "1" -> L2ChainConfig.ETHEREUM_MAINNET;
            case "sepolia", "11155111" -> L2ChainConfig.SEPOLIA;
            case "arbitrum", "arbitrumone", "42161" -> L2ChainConfig.ARBITRUM_ONE;
            case "arbitrumsepolia", "421614" -> L2ChainConfig.ARBITRUM_SEPOLIA;
            case "base", "basemainnet", "8453" -> L2ChainConfig.BASE_MAINNET;
            case "basesepolia", "84532" -> L2ChainConfig.BASE_SEPOLIA;
            case "optimism", "op", "10" -> L2ChainConfig.OPTIMISM;
            case "optimismsepolia", "opsepolia", "11155420" -> L2ChainConfig.OPTIMISM_SEPOLIA;
            case "polygon", "matic", "137" -> L2ChainConfig.POLYGON;
            case "polygonamoy", "amoy", "80002" -> L2ChainConfig.POLYGON_AMOY;
            case "zksync", "zksyncera", "era", "324" -> L2ChainConfig.ZKSYNC_ERA;
            case "zksyncsepolia", "300" -> L2ChainConfig.ZKSYNC_SEPOLIA;
            case "polygonzkevm", "zkevm", "1101" -> L2ChainConfig.POLYGON_ZKEVM;
            case "polygonzkevmcardona", "cardona", "2442" -> L2ChainConfig.POLYGON_ZKEVM_CARDONA;
            case "scroll", "534352" -> L2ChainConfig.SCROLL;
            case "scrollsepolia", "534351" -> L2ChainConfig.SCROLL_SEPOLIA;
            case "linea", "59144" -> L2ChainConfig.LINEA;
            case "lineasepolia", "59141" -> L2ChainConfig.LINEA_SEPOLIA;
            case "blast", "81457" -> L2ChainConfig.BLAST;
            case "blastsepolia", "168587773" -> L2ChainConfig.BLAST_SEPOLIA;
            case "local", "anvil", "hardhat", "31337" -> L2ChainConfig.LOCAL_ANVIL;
            default -> null;
        };
    }

    private static boolean isLocalChain(L2ChainConfig chain) {
        return chain.getChainType() == L2ChainConfig.ChainType.LOCAL;
    }

    /**
     * Resolve the RPC URL for a chain using env vars or defaults.
     *
     * <p>Priority:
     * <ol>
     *   <li>{@code DHRLANG_RPC_URL} env var (any chain)</li>
     *   <li>{@code RPC_URL} env var (common convention)</li>
     *   <li>Chain template with {@code INFURA_API_KEY} or {@code ALCHEMY_API_KEY}</li>
     *   <li>Default public URL (local chains only)</li>
     * </ol>
     */
    private static String resolveRpcUrl(L2ChainConfig chain) {
        // 1. Explicit env var
        String envRpc = System.getenv("DHRLANG_RPC_URL");
        if (envRpc != null && !envRpc.isBlank()) return envRpc;
        envRpc = System.getenv("RPC_URL");
        if (envRpc != null && !envRpc.isBlank()) return envRpc;

        // 2. Template with API key
        String template = chain.getRpcUrlTemplate();
        if (template != null && template.contains("{API_KEY}")) {
            String infuraKey = System.getenv("INFURA_API_KEY");
            if (infuraKey != null && !infuraKey.isBlank()) {
                return template.replace("{API_KEY}", infuraKey);
            }
            String alchemyKey = System.getenv("ALCHEMY_API_KEY");
            if (alchemyKey != null && !alchemyKey.isBlank()) {
                return template.replace("{API_KEY}", alchemyKey);
            }
        }

        // 3. Local chains have direct URLs
        if (isLocalChain(chain) && template != null && !template.contains("{API_KEY}")) {
            return template;
        }

        // 4. Some chains have public RPCs (no key needed)
        if (template != null && !template.contains("{API_KEY}")) {
            return template;
        }

        return null;
    }

    private static int getStorageSlotCount(Program program, String contractName) {
        try {
            var layouter = new StorageLayouter();
            layouter.layoutAll(program);
            var layout = layouter.getLayout(contractName);
            return layout != null ? layout.getSlots().size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parse blockchain CLI arguments from the main args array.
     */
    public static BlockchainOptions parseArgs(String[] args, int startIndex) {
        BlockchainOptions opts = new BlockchainOptions();
        for (int i = startIndex; i < args.length; i++) {
            String a = args[i];
            if (opts.subcommand == null && !a.startsWith("-")) {
                // First non-flag is the subcommand
                opts.subcommand = a;
            } else if (a.startsWith("--network=")) {
                opts.network = a.substring("--network=".length());
            } else if (a.startsWith("--output=")) {
                opts.outputDir = a.substring("--output=".length());
            } else if (a.startsWith("--address=")) {
                opts.address = a.substring("--address=".length());
            } else if (a.startsWith("--deploy-format=")) {
                opts.deployFormat = a.substring("--deploy-format=".length());
            } else if (a.startsWith("--etherscan-key=")) {
                opts.etherscanKey = a.substring("--etherscan-key=".length());
            } else if (a.startsWith("--keystore=")) {
                opts.keystorePath = a.substring("--keystore=".length());
            } else if (a.startsWith("--from=")) {
                opts.deployerFrom = a.substring("--from=".length()).trim();
            } else if (a.startsWith("--runs=")) {
                try {
                    opts.fuzzRuns = Integer.parseInt(a.substring("--runs=".length()).trim());
                } catch (NumberFormatException ignored) { /* keep default */ }
            } else if (a.startsWith("--seed=")) {
                try {
                    opts.fuzzSeed = Long.parseLong(a.substring("--seed=".length()).trim());
                } catch (NumberFormatException ignored) { /* keep default */ }
            } else if (a.startsWith("--bound=")) {
                try {
                    opts.proveBound = Integer.parseInt(a.substring("--bound=".length()).trim());
                } catch (NumberFormatException ignored) { /* keep default */ }
            } else if (a.startsWith("--fail-on=")) {
                opts.failOn = a.substring("--fail-on=".length()).trim();
            } else if (a.startsWith("--format=")) {
                opts.exportFormat = a.substring("--format=".length()).trim();
            } else if (a.startsWith("--name=")) {
                opts.stdlibCustomName = a.substring("--name=".length()).trim();
            } else if (a.startsWith("--version=")) {
                opts.entrypointVersion = a.substring("--version=".length()).trim();
            } else if (a.startsWith("--sender=")) {
                opts.accountSender = a.substring("--sender=".length()).trim();
            } else if (a.startsWith("--nonce=")) {
                opts.accountNonce = a.substring("--nonce=".length()).trim();
            } else if (a.startsWith("--init-code=")) {
                opts.accountInitCode = a.substring("--init-code=".length()).trim();
            } else if (a.startsWith("--call-data=")) {
                opts.accountCallData = a.substring("--call-data=".length()).trim();
            } else if (a.startsWith("--paymaster-data=")) {
                opts.accountPaymasterData = a.substring("--paymaster-data=".length()).trim();
            } else if (a.startsWith("--call-gas=")) {
                opts.accountCallGas = a.substring("--call-gas=".length()).trim();
            } else if (a.startsWith("--verification-gas=")) {
                opts.accountVerificationGas = a.substring("--verification-gas=".length()).trim();
            } else if (a.startsWith("--pre-verification-gas=")) {
                opts.accountPreVerificationGas = a.substring("--pre-verification-gas=".length()).trim();
            } else if (a.startsWith("--max-priority-fee=")) {
                opts.accountMaxPriorityFee = a.substring("--max-priority-fee=".length()).trim();
            } else if (a.startsWith("--max-fee=")) {
                opts.accountMaxFee = a.substring("--max-fee=".length()).trim();
            } else if ("--dry-run".equals(a)) {
                opts.dryRun = true;
            } else if ("--verify".equals(a)) {
                opts.verify = true;
            } else if ("--json".equals(a)) {
                opts.json = true;
            } else if ("--verbose".equals(a) || "-v".equals(a)) {
                opts.verbose = true;
            } else if (!a.startsWith("-") && opts.subcommand != null) {
                // Subcommand-specific positional arg (e.g., "wallet create")
                if ("wallet".equals(opts.subcommand) && opts.walletAction == null) {
                    opts.walletAction = a;
                } else if ("stdlib".equals(opts.subcommand) && opts.stdlibAction == null) {
                    opts.stdlibAction = a;
                } else if ("stdlib".equals(opts.subcommand) && opts.stdlibName == null) {
                    opts.stdlibName = a;
                } else if ("account".equals(opts.subcommand) && opts.accountAction == null) {
                    opts.accountAction = a;
                } else if (a.endsWith(".dhr")) {
                    // Record the source file for report locations (SARIF/markdown)
                    opts.sourceFile = a;
                }
                // else: handled by Main.java
            }
        }
        return opts;
    }
}
