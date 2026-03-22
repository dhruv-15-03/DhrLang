package dhrlang.evm;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level orchestrator that compiles DhrLang smart contracts to EVM bytecode.
 *
 * <p>Usage:
 * <pre>{@code
 *   Program program = parser.parse();
 *   EvmContractCompiler compiler = new EvmContractCompiler(program);
 *   List<EvmContractCompiler.ContractArtifact> artifacts = compiler.compileAll();
 *   for (ContractArtifact a : artifacts) {
 *       System.out.println(a.getContractName());
 *       System.out.println("ABI: " + a.getAbiJson());
 *       System.out.println("Bytecode: " + a.getCreationBytecodeHex());
 *   }
 * }</pre>
 */
public final class EvmContractCompiler {

    private final Program program;
    private final ErrorReporter errorReporter;

    // ── Constructors ─────────────────────────────────────────────────────────────

    public EvmContractCompiler(Program program) {
        this(program, null);
    }

    public EvmContractCompiler(Program program, ErrorReporter errorReporter) {
        this.program = program;
        this.errorReporter = errorReporter;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Compile all {@code @contract} classes in the program.
     *
     * @return a list of {@link ContractArtifact} — one per contract
     */
    public List<ContractArtifact> compileAll() {
        // 1. Run StorageLayouter to assign slots for all contracts
        StorageLayouter storageLayouter = new StorageLayouter();
        storageLayouter.layoutAll(program);

        List<ContractArtifact> artifacts = new ArrayList<>();

        for (ClassDecl classDecl : program.getClasses()) {
            if (!classDecl.isContract()) continue;

            ContractLayout layout = storageLayouter.getLayout(classDecl.getName());
            ContractArtifact artifact = compileSingle(classDecl, layout);
            artifacts.add(artifact);
        }

        return artifacts;
    }

    /**
     * Compile a single {@code @contract} class.
     *
     * @param classDecl the contract class declaration
     * @param layout    the pre-computed storage layout (may be null)
     * @return the compiled artifact
     */
    public ContractArtifact compileSingle(ClassDecl classDecl, ContractLayout layout) {
        // Build class registry for inheritance resolution
        Map<String, ClassDecl> classRegistry = new java.util.HashMap<>();
        for (ClassDecl cd : program.getClasses()) {
            classRegistry.put(cd.getName(), cd);
        }
        EvmCodeGen codeGen = new EvmCodeGen(classDecl, layout, classRegistry);
        EvmCodeGen.CompilationResult result = codeGen.compile();

        return new ContractArtifact(
                classDecl.getName(),
                result.getCreationBytecode(),
                result.getRuntimeBytecode(),
                result.getAbiJson(),
                result.getAbi(),
                layout,
                estimateGas(result.getCreationBytecode())
        );
    }

    // ── Gas estimation ───────────────────────────────────────────────────

    /**
     * Estimate the deployment gas cost for creation bytecode.
     *
     * <p>The EVM charges:
     * <ul>
     *   <li>21,000 base transaction cost</li>
     *   <li>4 gas per zero byte in the transaction data</li>
     *   <li>16 gas per non-zero byte in the transaction data</li>
     *   <li>32,000 for the CREATE opcode</li>
     *   <li>200 gas per byte of deployed runtime code (stored on-chain)</li>
     * </ul>
     */
    static long estimateGas(byte[] creationBytecode) {
        long gas = 21_000L;  // base tx cost
        gas += 32_000L;      // CREATE overhead

        // Calldata cost
        for (byte b : creationBytecode) {
            gas += (b == 0) ? 4 : 16;
        }

        return gas;
    }

    /**
     * Estimate runtime gas cost for a function call based on bytecode size.
     * This is a rough estimate based on average opcode costs.
     *
     * @param runtimeBytecode the runtime bytecode
     * @return estimated gas for a typical function call
     */
    static long estimateRuntimeGas(byte[] runtimeBytecode) {
        long gas = 21_000L;  // base tx cost
        // Average 5 gas per byte of runtime code executed (rough heuristic)
        gas += (long) runtimeBytecode.length * 5;
        return gas;
    }

    // ── ContractArtifact ─────────────────────────────────────────────────

    /**
     * The compiled output for a single contract, containing everything
     * needed for deployment and interaction.
     */
    public static final class ContractArtifact {
        private final String contractName;
        private final byte[] creationBytecode;
        private final byte[] runtimeBytecode;
        private final String abiJson;
        private final List<Map<String, Object>> abi;
        private final ContractLayout storageLayout;
        private final long estimatedDeployGas;

        ContractArtifact(String contractName, byte[] creationBytecode,
                         byte[] runtimeBytecode, String abiJson,
                         List<Map<String, Object>> abi,
                         ContractLayout storageLayout,
                         long estimatedDeployGas) {
            this.contractName = contractName;
            this.creationBytecode = creationBytecode;
            this.runtimeBytecode = runtimeBytecode;
            this.abiJson = abiJson;
            this.abi = abi;
            this.storageLayout = storageLayout;
            this.estimatedDeployGas = estimatedDeployGas;
        }

        public String getContractName() { return contractName; }
        public byte[] getCreationBytecode() { return creationBytecode; }
        public byte[] getRuntimeBytecode() { return runtimeBytecode; }
        public String getAbiJson() { return abiJson; }
        public List<Map<String, Object>> getAbi() { return abi; }
        public ContractLayout getStorageLayout() { return storageLayout; }
        public long getEstimatedDeployGas() { return estimatedDeployGas; }

        public String getCreationBytecodeHex() {
            return FunctionSelector.bytesToHex(creationBytecode);
        }

        public String getRuntimeBytecodeHex() {
            return FunctionSelector.bytesToHex(runtimeBytecode);
        }

        /**
         * Generate a human-readable summary of this contract artifact.
         */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Contract: ").append(contractName).append('\n');
            sb.append("  Creation bytecode: ").append(creationBytecode.length).append(" bytes\n");
            sb.append("  Runtime bytecode:  ").append(runtimeBytecode.length).append(" bytes\n");
            sb.append("  ABI entries:       ").append(abi.size()).append('\n');
            sb.append("  Est. deploy gas:   ").append(estimatedDeployGas).append('\n');
            if (storageLayout != null) {
                sb.append("  Storage slots:     ").append(storageLayout.getTotalSlots()).append('\n');
            }
            return sb.toString();
        }
    }
}
