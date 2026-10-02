package dhrlang.stdlib;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the {@link ContractStdlib} base-contract catalog: every template must
 * compile cleanly through the real pipeline (Lexer -> Parser -> TypeChecker ->
 * EvmContractCompiler) and produce a non-empty ABI, so the
 * {@code dhrlang contract stdlib} scaffolds are always usable starting points.
 */
@DisplayName("v4 stdlib: standard contract catalog")
class ContractStdlibTest {

    private record CompileResult(boolean ok, String detail, int abiEntries, String abiJson) {}

    private CompileResult compile(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        if (errors.hasErrors()) {
            return new CompileResult(false, "lexer errors=" + errors.getErrorCount(), 0, null);
        }
        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        if (errors.hasErrors()) {
            return new CompileResult(false, "parser errors=" + errors.getErrorCount(), 0, null);
        }
        TypeChecker checker = new TypeChecker(errors);
        checker.check(program);
        if (errors.hasErrors()) {
            StringBuilder sb = new StringBuilder("typechecker errors:");
            errors.getErrors().forEach(e -> sb.append("\n      ").append(e.toString()));
            return new CompileResult(false, sb.toString(), 0, null);
        }
        EvmContractCompiler compiler = new EvmContractCompiler(program, errors);
        List<ContractArtifact> artifacts = compiler.compileAll();
        if (errors.hasErrors()) {
            return new CompileResult(false, "compiler errors=" + errors.getErrorCount(), 0, null);
        }
        if (artifacts.isEmpty()) {
            return new CompileResult(false, "no @contract artifacts produced", 0, null);
        }
        String abi = artifacts.get(0).getAbiJson();
        int entries = (abi == null) ? 0 : abi.split("\\{\"type\"").length - 1;
        return new CompileResult(true, "ok", entries, abi);
    }

    @Test
    @DisplayName("every catalog entry compiles to a non-empty ABI")
    void everyTemplateCompiles() {
        List<String> failures = new ArrayList<>();
        for (String name : ContractStdlib.availableContracts()) {
            String src = ContractStdlib.getByName(name);
            assertNotNull(src, "getByName returned null for " + name);
            CompileResult r = compile(src);
            System.out.printf("  %-20s -> %s (abiEntries=%d)%n", name, r.detail(), r.abiEntries());
            if (!r.ok()) {
                failures.add(name + ": " + r.detail());
            } else if (r.abiEntries() == 0) {
                failures.add(name + ": empty ABI");
            }
        }
        assertTrue(failures.isEmpty(), "templates failed to compile: " + failures);
    }

    @Test
    @DisplayName("catalog and registry agree (8 named contracts, all resolvable)")
    void catalogConsistency() {
        List<String> names = ContractStdlib.availableContracts();
        assertEquals(8, names.size(), "expected 8 standard contracts");
        assertEquals(names.size(), ContractStdlib.catalog().size(), "catalog size mismatch");
        for (String name : names) {
            assertNotNull(ContractStdlib.catalog().get(name), "catalog missing " + name);
            assertNotNull(ContractStdlib.getByName(name), "getByName missing " + name);
        }
        assertNull(ContractStdlib.getByName("DoesNotExist"));
    }

    @Test
    @DisplayName("ERC20 template exposes the EIP-20 surface in its ABI")
    void erc20AbiSurface() {
        CompileResult r = compile(ContractStdlib.erc20Base());
        assertTrue(r.ok(), r.detail());
        String abi = r.abiJson();
        assertNotNull(abi);
        for (String member : List.of("transfer", "mint", "burn", "getTotalSupply", "Transfer", "Approval")) {
            assertTrue(abi.contains("\"" + member + "\""), "ERC20 ABI missing " + member + ": " + abi);
        }
    }
}
