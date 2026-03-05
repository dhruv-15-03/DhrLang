package dhrlang.evm;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.ContractAnnotation;
import dhrlang.ast.FunctionDecl;
import dhrlang.ast.VarDecl;
import dhrlang.types.BlockchainTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a Solidity-compatible ABI (Application Binary Interface) from a
 * {@code @contract} class declaration.
 *
 * <p>The ABI describes how to encode/decode function calls and events for
 * interaction with deployed EVM bytecode.  The output is a {@code List<Map>}
 * that can be serialised to JSON.</p>
 */
public final class AbiGenerator {

    private AbiGenerator() {}

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Generate the full ABI for a contract class.
     *
     * @param classDecl a {@code @contract} annotated class declaration
     * @return ordered list of ABI entries (each a {@code Map<String,Object>})
     */
    public static List<Map<String, Object>> generate(ClassDecl classDecl) {
        List<Map<String, Object>> abi = new ArrayList<>();

        for (FunctionDecl fn : classDecl.getFunctions()) {
            if (fn.isContractConstructor()) {
                abi.add(buildConstructorEntry(fn));
            } else if (fn.hasContractAnnotation(ContractAnnotation.EVENT)) {
                abi.add(buildEventEntry(fn));
            } else {
                abi.add(buildFunctionEntry(fn));
            }
        }

        // Receive function — implied for @payable contracts
        if (classDecl.hasContractAnnotation(ContractAnnotation.PAYABLE)) {
            abi.add(buildReceiveEntry());
        }

        return abi;
    }

    /**
     * Serialise an ABI to a compact JSON string.
     */
    public static String toJson(List<Map<String, Object>> abi) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < abi.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(mapToJson(abi.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    // ── Entry builders ──────────────────────────────────────────────────

    private static Map<String, Object> buildFunctionEntry(FunctionDecl fn) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "function");
        entry.put("name", fn.getName());
        entry.put("inputs", buildInputs(fn.getParameters()));
        entry.put("outputs", buildOutputs(fn.getReturnType()));
        entry.put("stateMutability", stateMutability(fn));
        return entry;
    }

    private static Map<String, Object> buildConstructorEntry(FunctionDecl fn) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "constructor");
        entry.put("inputs", buildInputs(fn.getParameters()));
        entry.put("stateMutability", fn.isPayable() ? "payable" : "nonpayable");
        return entry;
    }

    private static Map<String, Object> buildEventEntry(FunctionDecl fn) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "event");
        entry.put("name", fn.getName());

        List<Map<String, Object>> inputs = new ArrayList<>();
        for (int i = 0; i < fn.getParameters().size(); i++) {
            VarDecl p = fn.getParameters().get(i);
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", p.getName());
            param.put("type", solidityType(p.getType()));
            // First parameter of an event is typically indexed
            param.put("indexed", i == 0);
            inputs.add(param);
        }
        entry.put("inputs", inputs);
        entry.put("anonymous", false);
        return entry;
    }

    private static Map<String, Object> buildReceiveEntry() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "receive");
        entry.put("stateMutability", "payable");
        return entry;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static List<Map<String, Object>> buildInputs(List<VarDecl> params) {
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (VarDecl p : params) {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", p.getName());
            param.put("type", solidityType(p.getType()));
            inputs.add(param);
        }
        return inputs;
    }

    private static List<Map<String, Object>> buildOutputs(String returnType) {
        List<Map<String, Object>> outputs = new ArrayList<>();
        if (returnType != null && !returnType.equals("void") && !returnType.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", "");
            out.put("type", solidityType(returnType));
            outputs.add(out);
        }
        return outputs;
    }

    /**
     * Determine the Solidity ABI state mutability string.
     */
    static String stateMutability(FunctionDecl fn) {
        if (fn.isPure()) return "pure";
        if (fn.isView()) return "view";
        if (fn.isPayable()) return "payable";
        return "nonpayable";
    }

    /**
     * Convert a DhrLang type name to a Solidity ABI type.
     */
    static String solidityType(String dhrType) {
        if (dhrType == null) return "uint256";  // fallback
        String sol = BlockchainTypes.toSolidityType(dhrType);
        // Map DhrLang primitive types that don't exist in BlockchainTypes
        switch (sol) {
            case "num":    return "uint256";
            case "str":    return "string";
            case "bool":   return "bool";
            case "void":   return "";
            default:       return sol;
        }
    }

    /**
     * Compute the function selector for an ABI function entry.
     */
    public static byte[] functionSelector(FunctionDecl fn) {
        StringBuilder sig = new StringBuilder(fn.getName()).append('(');
        List<VarDecl> params = fn.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append(solidityType(params.get(i).getType()));
        }
        sig.append(')');
        return FunctionSelector.compute(sig.toString());
    }

    /**
     * Compute the event topic (full keccak256 hash of signature).
     */
    public static byte[] eventTopic(FunctionDecl fn) {
        StringBuilder sig = new StringBuilder(fn.getName()).append('(');
        List<VarDecl> params = fn.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sig.append(',');
            sig.append(solidityType(params.get(i).getType()));
        }
        sig.append(')');
        return FunctionSelector.keccak256(sig.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ── Minimal JSON serialiser (no external dependency) ────────────────

    @SuppressWarnings("unchecked")
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            sb.append(valueToJson(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(valueToJson(list.get(i)));
            }
            sb.append(']');
            return sb.toString();
        }
        if (value instanceof Map) {
            return mapToJson((Map<String, Object>) value);
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
