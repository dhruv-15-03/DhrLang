package dhrlang.interop;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.evm.EvmContractCompiler;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InteropExporter} — verifies the Hardhat, Foundry and
 * viem/wagmi artifact shapes, ABI splicing and {@code 0x}-prefixed bytecode.
 */
@DisplayName("v4 interop: framework artifact export")
class InteropExporterTest {

    private static final String VERSION = "3.7.0";

    private static final String COUNTER_SRC = """
            @contract
            class Counter {
                @storage num value;

                @view
                kaam getValue() {
                    return value;
                }

                kaam setValue(num v) {
                    value = v;
                }
            }
            """;

    // ── Helpers ──────────────────────────────────────────────────────────

    private ContractArtifact compileOne(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        assertFalse(errors.hasErrors(), "Lexer errors: " + errors.getErrorCount());

        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        assertFalse(errors.hasErrors(), "Parser errors: " + errors.getErrorCount());

        TypeChecker checker = new TypeChecker(errors);
        checker.check(program);

        EvmContractCompiler compiler = new EvmContractCompiler(program, errors);
        List<ContractArtifact> artifacts = compiler.compileAll();
        assertFalse(artifacts.isEmpty(), "Expected at least one @contract class");
        return artifacts.get(0);
    }

    // ── Hardhat ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("hardhat artifact carries _format, names, 0x bytecode and spliced ABI")
    void hardhatArtifactShape() {
        ContractArtifact a = compileOne(COUNTER_SRC);
        String json = InteropExporter.hardhatArtifact(a, "Counter.dhr", VERSION);

        assertTrue(json.contains("\"_format\": \"hh-sol-artifact-1\""), json);
        assertTrue(json.contains("\"contractName\": \"Counter\""), json);
        assertTrue(json.contains("\"sourceName\": \"Counter.dhr\""), json);
        assertTrue(json.contains("\"compiler\": \"dhrlang-3.7.0\""), json);
        assertTrue(json.contains("\"bytecode\": \"0x"), json);
        assertTrue(json.contains("\"deployedBytecode\": \"0x"), json);
        assertTrue(json.contains("\"linkReferences\": {}"), json);
        assertTrue(json.contains("\"deployedLinkReferences\": {}"), json);
        // ABI spliced verbatim
        assertTrue(json.contains("\"type\":\"function\""), json);
        assertTrue(json.contains("getValue"), json);
        assertTrue(json.contains("setValue"), json);
    }

    // ── Foundry ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("foundry artifact nests bytecode under object and records metadata")
    void foundryArtifactShape() {
        ContractArtifact a = compileOne(COUNTER_SRC);
        String json = InteropExporter.foundryArtifact(a, "Counter.dhr", VERSION);

        assertTrue(json.contains("\"bytecode\": { \"object\": \"0x"), json);
        assertTrue(json.contains("\"deployedBytecode\": { \"object\": \"0x"), json);
        assertTrue(json.contains("\"language\": \"DhrLang\""), json);
        assertTrue(json.contains("\"version\": \"dhrlang-3.7.0\""), json);
        assertTrue(json.contains("\"Counter.dhr\": \"Counter\""), json);
        assertTrue(json.contains("getValue"), json);
    }

    // ── viem / wagmi ─────────────────────────────────────────────────────

    @Test
    @DisplayName("viem module exports ABI as const + 0x bytecode consts")
    void viemModuleShape() {
        ContractArtifact a = compileOne(COUNTER_SRC);
        String ts = InteropExporter.viemModule(a, VERSION);

        assertTrue(ts.contains("export const counterAbi = ["), ts);
        assertTrue(ts.contains("] as const;"), ts);
        assertTrue(ts.contains("export const counterBytecode = \"0x"), ts);
        assertTrue(ts.contains("export const counterDeployedBytecode = \"0x"), ts);
        assertTrue(ts.contains("\" as const;"), ts);
        assertTrue(ts.contains("getValue"), ts);
    }

    @Test
    @DisplayName("ts barrel re-exports every generated module")
    void tsBarrelReExports() {
        String barrel = InteropExporter.tsBarrel(List.of("Counter", "Token"), VERSION);
        assertTrue(barrel.contains("export * from \"./Counter\";"), barrel);
        assertTrue(barrel.contains("export * from \"./Token\";"), barrel);
    }

    // ── Edge cases (hand-built artifacts) ────────────────────────────────

    @Test
    @DisplayName("empty/absent bytecode and ABI degrade to 0x and []")
    void emptyArtifactStubs() {
        ContractArtifact empty = new ContractArtifact("Empty", null, null, null, null, null, 0L);

        String hardhat = InteropExporter.hardhatArtifact(empty, "Empty.dhr", VERSION);
        assertTrue(hardhat.contains("\"bytecode\": \"0x\""), hardhat);
        assertTrue(hardhat.contains("\"deployedBytecode\": \"0x\""), hardhat);
        assertTrue(hardhat.contains("\"abi\": []"), hardhat);

        String ts = InteropExporter.viemModule(empty, VERSION);
        assertTrue(ts.contains("export const emptyAbi = [] as const;"), ts);
        assertTrue(ts.contains("export const emptyBytecode = \"0x\" as const;"), ts);
    }

    @Test
    @DisplayName("bytecode is 0x-prefixed lowercase hex of the raw bytes")
    void bytecodeHexEncoding() {
        byte[] creation = {0x60, (byte) 0x80, 0x60, 0x40};
        byte[] runtime = {(byte) 0xfe};
        ContractArtifact a = new ContractArtifact("Probe", creation, runtime,
                "[{\"type\":\"function\",\"name\":\"ping\"}]", null, null, 7L);

        String hardhat = InteropExporter.hardhatArtifact(a, "Probe.dhr", VERSION);
        assertTrue(hardhat.contains("\"bytecode\": \"0x60806040\""), hardhat);
        assertTrue(hardhat.contains("\"deployedBytecode\": \"0xfe\""), hardhat);

        String foundry = InteropExporter.foundryArtifact(a, "Probe.dhr", VERSION);
        assertTrue(foundry.contains("\"bytecode\": { \"object\": \"0x60806040\" }"), foundry);

        String ts = InteropExporter.viemModule(a, VERSION);
        assertTrue(ts.contains("export const probeBytecode = \"0x60806040\" as const;"), ts);
        assertTrue(ts.contains("export const probeDeployedBytecode = \"0xfe\" as const;"), ts);
    }
}
