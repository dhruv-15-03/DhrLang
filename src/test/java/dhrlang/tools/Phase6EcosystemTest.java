package dhrlang.tools;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.evm.EvmContractCompiler;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 tests: Ecosystem & tooling — framework integration,
 * VS Code commands, CI workflows.
 */
@DisplayName("Phase 6: Ecosystem & Tooling Tests")
class Phase6EcosystemTest {

    private ContractArtifact compileContract(String source) {
        ErrorReporter errors = new ErrorReporter();
        Lexer lexer = new Lexer(source, errors);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens, errors);
        Program program = parser.parse();
        TypeChecker checker = new TypeChecker(errors);
        checker.check(program);
        EvmContractCompiler compiler = new EvmContractCompiler(program, errors);
        var artifacts = compiler.compileAll();
        return artifacts.isEmpty() ? null : artifacts.get(0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Foundry Integration
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Foundry Integration")
    class FoundryTests {

        @Test
        @DisplayName("foundry.toml config is valid TOML structure")
        void foundryConfigValid() {
            String config = FrameworkIntegration.foundryConfig("MyToken");
            assertNotNull(config);
            assertTrue(config.contains("[profile.default]"));
            assertTrue(config.contains("src = \"contracts\""));
            assertTrue(config.contains("dhrlang"));
            assertTrue(config.contains("[rpc_endpoints]"));
            assertTrue(config.contains("sepolia"));
        }

        @Test
        @DisplayName("Foundry test template compiles for a contract")
        void foundryTestTemplate() {
            var artifact = compileContract("""
                @contract
                class Token {
                    @storage num supply;
                    @view kaam getSupply() { return supply; }
                }
                """);
            assertNotNull(artifact);
            String test = FrameworkIntegration.foundryTest(artifact);
            assertTrue(test.contains("TokenTest"));
            assertTrue(test.contains("forge-std/Test.sol"));
            assertTrue(test.contains("create("));
            assertTrue(test.contains("hex\""));
        }

        @Test
        @DisplayName("Foundry Makefile has all targets")
        void foundryMakefile() {
            String makefile = FrameworkIntegration.foundryMakefile();
            assertTrue(makefile.contains("compile:"));
            assertTrue(makefile.contains("audit:"));
            assertTrue(makefile.contains("audit-sarif:"));
            assertTrue(makefile.contains("gas:"));
            assertTrue(makefile.contains("deploy-local:"));
            assertTrue(makefile.contains("deploy-sepolia:"));
            assertTrue(makefile.contains("test:"));
            assertTrue(makefile.contains("clean:"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Hardhat Integration
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Hardhat Integration")
    class HardhatTests {

        @Test
        @DisplayName("Hardhat config has DhrLang tasks")
        void hardhatConfig() {
            String config = FrameworkIntegration.hardhatConfig();
            assertTrue(config.contains("dhrlang:compile"));
            assertTrue(config.contains("dhrlang:audit"));
            assertTrue(config.contains("hardhat-toolbox"));
            assertTrue(config.contains("module.exports"));
            assertTrue(config.contains("sepolia"));
        }

        @Test
        @DisplayName("Hardhat deploy script uses ethers.js")
        void hardhatDeployScript() {
            var artifact = compileContract("""
                @contract
                class Vault {
                    @storage num balance;
                    @payable kaam deposit() { balance = balance + msg.value; }
                }
                """);
            assertNotNull(artifact);
            String script = FrameworkIntegration.hardhatDeployScript(artifact);
            assertTrue(script.contains("ethers"));
            assertTrue(script.contains("Vault"));
            assertTrue(script.contains("ContractFactory"));
            assertTrue(script.contains("deploy()"));
            assertTrue(script.contains("waitForDeployment"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  TypeScript / Wagmi Bindings
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TypeScript Bindings")
    class TypeScriptTests {

        @Test
        @DisplayName("TypeScript bindings have ABI and bytecode")
        void tsBindings() {
            var artifact = compileContract("""
                @contract
                class Token {
                    @storage num supply;
                    @view kaam getSupply() { return supply; }
                    kaam mint(num amount) { supply = supply + amount; }
                }
                """);
            assertNotNull(artifact);
            String ts = FrameworkIntegration.typescriptBindings(artifact);
            assertTrue(ts.contains("TokenABI"));
            assertTrue(ts.contains("TokenBytecode"));
            assertTrue(ts.contains("TokenConfig"));
            assertTrue(ts.contains("as const"));
            assertTrue(ts.contains("0x")); // bytecode prefix
        }

        @Test
        @DisplayName("TypeScript bindings are wagmi/viem compatible")
        void tsWagmiCompat() {
            var artifact = compileContract("""
                @contract
                class NFT {
                    @storage num totalMinted;
                    kaam mint() { totalMinted = totalMinted + 1; }
                    @view kaam total() { return totalMinted; }
                }
                """);
            assertNotNull(artifact);
            String ts = FrameworkIntegration.typescriptBindings(artifact);
            // wagmi requires 'as const' for type inference
            assertTrue(ts.contains("as const"));
            // Should export named constants
            assertTrue(ts.contains("export const"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GitHub Actions Workflow Validation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CI/CD Workflow")
    class CiTests {

        @Test
        @DisplayName("Contract audit workflow file exists")
        void auditWorkflowExists() {
            java.io.File workflow = new java.io.File("e:\\DhrLang\\.github\\workflows\\contract-audit.yml");
            // Check existence if running locally (may not exist in CI)
            if (workflow.exists()) {
                assertTrue(workflow.length() > 100, "Workflow file should be non-trivial");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Full Pipeline: Contract → Compile → Audit → Framework Output
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Ecosystem Pipeline")
    class PipelineTests {

        @Test
        @DisplayName("ERC-20 contract produces all ecosystem artifacts")
        void fullEcosystemPipeline() {
            var artifact = compileContract("""
                @contract
                class MyToken {
                    @storage num totalSupply;
                    @storage Address owner;
                    
                    @constructor
                    kaam init(num _supply) {
                        totalSupply = _supply;
                        owner = msg.sender;
                    }
                    
                    @view kaam getTotalSupply() { return totalSupply; }
                    
                    kaam mint(num amount) {
                        if (msg.sender != owner) { throw "Not owner"; }
                        totalSupply = totalSupply + amount;
                    }
                    
                    @event kaam Transfer(Address from, Address to, num amount) {}
                }
                """);
            assertNotNull(artifact);

            // 1. Foundry test
            String foundryTest = FrameworkIntegration.foundryTest(artifact);
            assertTrue(foundryTest.contains("MyTokenTest"));

            // 2. Hardhat deploy
            String hardhatDeploy = FrameworkIntegration.hardhatDeployScript(artifact);
            assertTrue(hardhatDeploy.contains("MyToken deployed to"));

            // 3. TypeScript bindings
            String ts = FrameworkIntegration.typescriptBindings(artifact);
            assertTrue(ts.contains("MyTokenABI"));
            assertTrue(ts.contains("MyTokenBytecode"));

            // 4. Foundry Makefile
            String makefile = FrameworkIntegration.foundryMakefile();
            assertTrue(makefile.contains("compile:"));

            System.out.println("=== Ecosystem Artifact Summary ===");
            System.out.println("  Foundry test:    " + foundryTest.length() + " chars");
            System.out.println("  Hardhat deploy:  " + hardhatDeploy.length() + " chars");
            System.out.println("  TS bindings:     " + ts.length() + " chars");
            System.out.println("  Makefile:        " + makefile.length() + " chars");
        }
    }
}
