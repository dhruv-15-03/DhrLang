package dhrlang.interop;

import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.evm.FunctionSelector;

import java.util.List;

/**
 * Exports compiled {@link ContractArtifact}s into framework-ready interop files
 * that downstream EVM tooling can consume directly:
 *
 * <ul>
 *   <li><b>Hardhat</b> - {@code hh-sol-artifact-1} JSON ({@code {abi, bytecode,
 *       deployedBytecode}}), readable by Hardhat, ethers, viem and wagmi.</li>
 *   <li><b>Foundry</b> - {@code {abi, bytecode:{object}, deployedBytecode:{object}}}
 *       JSON matching the shape forge writes under {@code out/}.</li>
 *   <li><b>viem / wagmi</b> - a TypeScript module exporting the ABI {@code as const}
 *       (which is what unlocks viem's compile-time type inference) plus the
 *       creation/runtime bytecode.</li>
 * </ul>
 *
 * <p>Every method is a pure, side-effect-free {@code String} producer so the
 * output is trivially unit-testable. The ABI is spliced in verbatim from
 * {@link ContractArtifact#getAbiJson()} (already a compact JSON array) and all
 * bytecode is emitted {@code 0x}-prefixed, as the consuming tools expect.</p>
 *
 * <p>All generated output is plain ASCII so artifacts are byte-for-byte
 * deterministic regardless of the platform default charset used to compile.</p>
 */
public final class InteropExporter {

    private InteropExporter() {}

    // --- Hardhat ------------------------------------------------------------

    /**
     * Build a Hardhat-format artifact ({@code hh-sol-artifact-1}).
     *
     * @param artifact        the compiled contract
     * @param sourceName      logical source file name (e.g. {@code Token.dhr})
     * @param compilerVersion DhrLang compiler version string
     * @return pretty-printed Hardhat artifact JSON
     */
    public static String hardhatArtifact(ContractArtifact artifact, String sourceName,
                                         String compilerVersion) {
        String src = sourceName != null ? sourceName : artifact.getContractName() + ".dhr";
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_format\": \"hh-sol-artifact-1\",\n");
        sb.append("  \"contractName\": ").append(jsonStr(artifact.getContractName())).append(",\n");
        sb.append("  \"sourceName\": ").append(jsonStr(src)).append(",\n");
        sb.append("  \"compiler\": ").append(jsonStr("dhrlang-" + safeVersion(compilerVersion))).append(",\n");
        sb.append("  \"abi\": ").append(abiOrEmpty(artifact)).append(",\n");
        sb.append("  \"bytecode\": ").append(jsonStr(hex0x(artifact.getCreationBytecode()))).append(",\n");
        sb.append("  \"deployedBytecode\": ").append(jsonStr(hex0x(artifact.getRuntimeBytecode()))).append(",\n");
        sb.append("  \"linkReferences\": {},\n");
        sb.append("  \"deployedLinkReferences\": {}\n");
        sb.append("}\n");
        return sb.toString();
    }

    // --- Foundry ------------------------------------------------------------

    /**
     * Build a Foundry-style artifact ({@code {abi, bytecode:{object},
     * deployedBytecode:{object}, metadata}}) matching the shape forge emits
     * under {@code out/}.
     *
     * @param artifact        the compiled contract
     * @param sourceName      logical source file name (e.g. {@code Token.dhr})
     * @param compilerVersion DhrLang compiler version string
     * @return pretty-printed Foundry artifact JSON
     */
    public static String foundryArtifact(ContractArtifact artifact, String sourceName,
                                         String compilerVersion) {
        String src = sourceName != null ? sourceName : artifact.getContractName() + ".dhr";
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"abi\": ").append(abiOrEmpty(artifact)).append(",\n");
        sb.append("  \"bytecode\": { \"object\": ")
          .append(jsonStr(hex0x(artifact.getCreationBytecode()))).append(" },\n");
        sb.append("  \"deployedBytecode\": { \"object\": ")
          .append(jsonStr(hex0x(artifact.getRuntimeBytecode()))).append(" },\n");
        sb.append("  \"metadata\": {\n");
        sb.append("    \"language\": \"DhrLang\",\n");
        sb.append("    \"compiler\": { \"version\": ")
          .append(jsonStr("dhrlang-" + safeVersion(compilerVersion))).append(" },\n");
        sb.append("    \"settings\": { \"compilationTarget\": { ")
          .append(jsonStr(src)).append(": ").append(jsonStr(artifact.getContractName()))
          .append(" } }\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // --- viem / wagmi -------------------------------------------------------

    /**
     * Build a viem/wagmi TypeScript module. The ABI is exported {@code as const}
     * so viem can infer fully-typed reads/writes; creation and runtime bytecode
     * are exported as {@code 0x}-prefixed const strings for deployment.
     *
     * @param artifact        the compiled contract
     * @param compilerVersion DhrLang compiler version string
     * @return TypeScript source
     */
    public static String viemModule(ContractArtifact artifact, String compilerVersion) {
        String name = artifact.getContractName();
        String id = lowerFirst(name);
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by DhrLang ").append(safeVersion(compilerVersion))
          .append(" - do not edit by hand.\n");
        sb.append("// The `as const` assertion lets viem/wagmi infer fully-typed contract calls.\n\n");
        sb.append("export const ").append(id).append("Abi = ")
          .append(abiOrEmpty(artifact)).append(" as const;\n\n");
        sb.append("export const ").append(id).append("Bytecode = ")
          .append(jsonStr(hex0x(artifact.getCreationBytecode()))).append(" as const;\n\n");
        sb.append("export const ").append(id).append("DeployedBytecode = ")
          .append(jsonStr(hex0x(artifact.getRuntimeBytecode()))).append(" as const;\n");
        return sb.toString();
    }

    /**
     * Build a TypeScript barrel ({@code index.ts}) re-exporting every generated
     * contract module.
     *
     * @param contractNames   contract module names (one TS file each)
     * @param compilerVersion DhrLang compiler version string
     * @return TypeScript barrel source
     */
    public static String tsBarrel(List<String> contractNames, String compilerVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by DhrLang ").append(safeVersion(compilerVersion))
          .append(" - do not edit by hand.\n");
        for (String name : contractNames) {
            sb.append("export * from ").append(jsonStr("./" + name)).append(";\n");
        }
        return sb.toString();
    }

    // --- Helpers ------------------------------------------------------------

    private static String abiOrEmpty(ContractArtifact artifact) {
        String abi = artifact.getAbiJson();
        return (abi == null || abi.isEmpty()) ? "[]" : abi;
    }

    private static String hex0x(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "0x";
        return "0x" + FunctionSelector.bytesToHex(bytes);
    }

    private static String safeVersion(String v) {
        return (v == null || v.isEmpty()) ? "(development)" : v;
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
