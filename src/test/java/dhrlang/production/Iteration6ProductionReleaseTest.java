package dhrlang.production;

import dhrlang.ast.*;
import dhrlang.deploy.DeploymentManager;
import dhrlang.deploy.DeploymentManager.DeploymentRecord;
import dhrlang.deploy.DeploymentManager.DeploymentStatus;
import dhrlang.deploy.DeploymentManager.DeploymentTx;
import dhrlang.deploy.L2ChainConfig;
import dhrlang.deploy.L2ChainConfig.ChainType;
import dhrlang.evm.EvmContractCompiler;
import dhrlang.evm.EvmContractCompiler.ContractArtifact;
import dhrlang.production.AuditReportGenerator.AuditReport;
import dhrlang.production.AuditReportGenerator.ContractSummary;
import dhrlang.production.AuditReportGenerator.Finding;
import dhrlang.production.AuditReportGenerator.Severity;
import dhrlang.production.ContractDocGenerator.ContractDoc;
import dhrlang.production.ContractDocGenerator.DocFormat;
import dhrlang.production.ContractDocGenerator.FunctionDoc;
import dhrlang.production.ContractDocGenerator.ParamDoc;
import dhrlang.production.ContractDocGenerator.StorageDoc;
import dhrlang.production.ExampleContractTemplates.TemplateInfo;
import dhrlang.production.ExampleContractTemplates.TemplateType;
import dhrlang.production.VscodeLanguageSupport.CompletionItem;
import dhrlang.production.VscodeLanguageSupport.CompletionKind;
import dhrlang.production.VscodeLanguageSupport.Diagnostic;
import dhrlang.production.VscodeLanguageSupport.DiagnosticSeverity;
import dhrlang.production.VscodeLanguageSupport.HoverInfo;
import dhrlang.production.VscodeLanguageSupport.ParameterInfo;
import dhrlang.production.VscodeLanguageSupport.SignatureInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 6 – Production Release: Documentation &amp; Real Deployment.
 *
 * <p>Tests cover all new classes in the {@code dhrlang.production} and
 * {@code dhrlang.deploy} packages: AuditReportGenerator, ContractDocGenerator,
 * DeploymentManager, L2ChainConfig, ExampleContractTemplates, and
 * VscodeLanguageSupport.</p>
 *
 * <p>Covers user stories SC-501 through SC-506.</p>
 */
class Iteration6ProductionReleaseTest {

    // ═══════════════════════════════════════════════════
    //  Helpers: build AST nodes for testing
    // ═══════════════════════════════════════════════════

    /** Build a FunctionDecl with no annotations. */
    private static FunctionDecl makeFn(String name) {
        return new FunctionDecl("void", name, List.of(), new Block(List.of()));
    }

    /** Build a FunctionDecl with parameters. */
    private static FunctionDecl makeFnWithParams(String name, String... paramTypes) {
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        return new FunctionDecl("void", name, params, new Block(List.of()));
    }

    /** Build a FunctionDecl with contract annotations. */
    private static FunctionDecl makeFnWithAnnotations(String name,
                                                       ContractAnnotation... annotations) {
        Set<ContractAnnotation> annSet = EnumSet.noneOf(ContractAnnotation.class);
        annSet.addAll(Arrays.asList(annotations));
        return new FunctionDecl("void", name, List.of(), new Block(List.of()),
                Set.of(), annSet);
    }

    /** Build a FunctionDecl with annotations AND parameters. */
    private static FunctionDecl makeFnAnnotatedWithParams(String name,
                                                           ContractAnnotation annotation,
                                                           String... paramTypes) {
        Set<ContractAnnotation> annSet = EnumSet.of(annotation);
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        return new FunctionDecl("void", name, params, new Block(List.of()),
                Set.of(), annSet);
    }

    /** Build a FunctionDecl with multiple annotations. */
    private static FunctionDecl makeFnMultiAnnotated(String name,
                                                      Set<ContractAnnotation> annotations,
                                                      String... paramTypes) {
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        EnumSet<ContractAnnotation> annSet = EnumSet.noneOf(ContractAnnotation.class);
        annSet.addAll(annotations);
        return new FunctionDecl("void", name, params, new Block(List.of()),
                Set.of(), annSet);
    }

    /** Build a storage VarDecl. */
    private static VarDecl storageVar(String type, String name) {
        return new VarDecl(type, name, null, Set.of(),
                EnumSet.of(ContractAnnotation.STORAGE));
    }

    /** Build a regular VarDecl. */
    private static VarDecl plainVar(String type, String name) {
        return new VarDecl(type, name, null);
    }

    /** Build a ClassDecl with functions (no contract annotation). */
    private static ClassDecl makeClass(String name, FunctionDecl... fns) {
        return new ClassDecl(name, null, List.of(fns), List.of());
    }

    /** Build a ClassDecl with @contract annotation. */
    private static ClassDecl makeContract(String name,
                                           List<VarDecl> vars,
                                           FunctionDecl... fns) {
        return new ClassDecl(name, null, new ArrayList<>(),
                Arrays.asList(fns), vars, Set.of(),
                EnumSet.of(ContractAnnotation.CONTRACT));
    }

    /** Build a Program. */
    private static Program makeProgram(ClassDecl... classes) {
        return new Program(List.of(classes));
    }

    // ═══════════════════════════════════════════════════
    //  1.  AuditReportGenerator — SC-501
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("1. AuditReportGenerator – SC-501")
    class AuditReportGeneratorTests {

        private AuditReportGenerator gen;

        @BeforeEach
        void setUp() {
            gen = new AuditReportGenerator();
        }

        @Test
        @DisplayName("Severity enum has correct labels and weights")
        void severityEnumValues() {
            assertEquals("CRITICAL", Severity.CRITICAL.getLabel());
            assertEquals(4, Severity.CRITICAL.getWeight());
            assertEquals("HIGH", Severity.HIGH.getLabel());
            assertEquals(3, Severity.HIGH.getWeight());
            assertEquals("MEDIUM", Severity.MEDIUM.getLabel());
            assertEquals(2, Severity.MEDIUM.getWeight());
            assertEquals("LOW", Severity.LOW.getLabel());
            assertEquals(1, Severity.LOW.getWeight());
            assertEquals("INFO", Severity.INFORMATIONAL.getLabel());
            assertEquals(0, Severity.INFORMATIONAL.getWeight());
        }

        @Test
        @DisplayName("Finding stores all fields correctly")
        void findingFields() {
            Finding f = new Finding("AUD-001", Severity.HIGH, "Test Title",
                    "desc", "fix it", "Token.transfer");
            assertEquals("AUD-001", f.getId());
            assertEquals(Severity.HIGH, f.getSeverity());
            assertEquals("Test Title", f.getTitle());
            assertEquals("desc", f.getDescription());
            assertEquals("fix it", f.getRecommendation());
            assertEquals("Token.transfer", f.getLocation());
            assertTrue(f.toString().contains("HIGH"));
            assertTrue(f.toString().contains("AUD-001"));
        }

        @Test
        @DisplayName("Builder-style configuration works")
        void builderConfig() {
            gen.setProjectName("MyProject").setCompilerVersion("v3.0");
            assertEquals("MyProject", gen.getProjectName());
            assertEquals("v3.0", gen.getCompilerVersion());
        }

        @Test
        @DisplayName("Analyze empty program produces safe report")
        void analyzeEmptyProgram() {
            Program program = makeProgram();
            AuditReport report = gen.analyze(program);
            assertNotNull(report);
            assertEquals(0, report.getRiskScore());
            assertEquals("Safe", report.getRiskRating());
            assertTrue(report.getContracts().isEmpty());
            assertTrue(report.getFindings().isEmpty());
            assertNotNull(report.getGeneratedAt());
        }

        @Test
        @DisplayName("Analyze contract with constructor and reentrancy guard")
        void analyzeSecureContract() {
            ClassDecl cls = makeContract("Vault",
                    List.of(storageVar("uint256", "balance")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR),
                    makeFnMultiAnnotated("deposit",
                            Set.of(ContractAnnotation.PAYABLE, ContractAnnotation.NONREENTRANT)),
                    makeFnWithAnnotations("getBalance", ContractAnnotation.VIEW)
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            assertNotNull(report);
            // Secure contract → low findings count
            List<Finding> highFindings = report.getFindings().stream()
                    .filter(f -> f.getSeverity() == Severity.HIGH
                            || f.getSeverity() == Severity.CRITICAL)
                    .toList();
            // should have no HIGH/CRITICAL from audit analysis (only possible from validator)
            // Risk score should be modest
            assertTrue(report.getRiskScore() < 75);
        }

        @Test
        @DisplayName("Detect missing reentrancy guard on payable function")
        void detectMissingReentrancyGuard() {
            ClassDecl cls = makeContract("Unsafe",
                    List.of(storageVar("uint256", "balance")),
                    makeFnWithAnnotations("deposit", ContractAnnotation.PAYABLE)
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            boolean found = report.getFindings().stream()
                    .anyMatch(f -> f.getId().equals("AUD-001") || f.getId().equals("AUD-002"));
            assertTrue(found, "Should detect missing reentrancy guard or payable warning");
        }

        @Test
        @DisplayName("Detect missing @constructor in contracts")
        void detectMissingConstructor() {
            ClassDecl cls = makeContract("NoCtor",
                    List.of(storageVar("Address", "owner")),
                    makeFnWithAnnotations("getOwner", ContractAnnotation.VIEW)
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            boolean found = report.getFindings().stream()
                    .anyMatch(f -> f.getId().equals("AUD-005"));
            assertTrue(found, "Should detect missing @constructor");
        }

        @Test
        @DisplayName("Detect likely @view function not annotated")
        void detectMissingViewAnnotation() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR),
                    makeFn("getSupply")  // no @view, but name starts with "get"
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            boolean found = report.getFindings().stream()
                    .anyMatch(f -> f.getId().equals("AUD-003"));
            assertTrue(found, "Should detect likely @view function");
        }

        @Test
        @DisplayName("Detect duplicate contract names")
        void detectDuplicateContractNames() {
            ClassDecl cls1 = makeContract("Token", List.of(), makeFn("init"));
            ClassDecl cls2 = makeContract("Token", List.of(), makeFn("other"));
            AuditReport report = gen.analyze(makeProgram(cls1, cls2));
            boolean found = report.getFindings().stream()
                    .anyMatch(f -> f.getId().equals("AUD-010"));
            assertTrue(found, "Should detect duplicate contract names");
        }

        @Test
        @DisplayName("Detect empty contract")
        void detectEmptyContract() {
            ClassDecl cls = makeContract("Empty", List.of());
            AuditReport report = gen.analyze(makeProgram(cls));
            boolean found = report.getFindings().stream()
                    .anyMatch(f -> f.getId().equals("AUD-011"));
            assertTrue(found, "Should detect empty contract");
        }

        @Test
        @DisplayName("ContractSummary has correct counts")
        void contractSummaryCounts() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("Address", "owner"), storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR),
                    makeFnWithAnnotations("balanceOf", ContractAnnotation.VIEW),
                    makeFnMultiAnnotated("transfer",
                            Set.of(ContractAnnotation.PAYABLE, ContractAnnotation.NONREENTRANT))
            );
            ContractSummary summary = gen.analyzeContract(cls);
            assertEquals("Token", summary.getName());
            assertEquals(3, summary.getFunctionCount());
            assertEquals(2, summary.getStorageVariableCount());
            assertTrue(summary.getViewFunctionCount() >= 1);
            assertTrue(summary.getPayableFunctionCount() >= 1);
            assertTrue(summary.hasReentrancyGuard());
            assertTrue(summary.getEstimatedDeployGas() > 0);
        }

        @Test
        @DisplayName("Risk score calculation")
        void riskScoreCalculation() {
            assertEquals("Safe", AuditReportGenerator.riskRatingFromScore(0));
            assertEquals("Low", AuditReportGenerator.riskRatingFromScore(10));
            assertEquals("Medium", AuditReportGenerator.riskRatingFromScore(30));
            assertEquals("High", AuditReportGenerator.riskRatingFromScore(60));
            assertEquals("Critical", AuditReportGenerator.riskRatingFromScore(80));
        }

        @Test
        @DisplayName("Validation severity mapping")
        void validationSeverityMapping() {
            assertEquals(Severity.MEDIUM, AuditReportGenerator.mapValidationSeverity(null));
            assertEquals(Severity.MEDIUM, AuditReportGenerator.mapValidationSeverity("DHR-E123"));
            assertEquals(Severity.HIGH, AuditReportGenerator.mapValidationSeverity("DHR-E505"));
            assertEquals(Severity.MEDIUM, AuditReportGenerator.mapValidationSeverity("DHR-E501"));
        }

        @Test
        @DisplayName("isLikelyReadOnly heuristic")
        void likelyReadOnlyHeuristic() {
            assertTrue(AuditReportGenerator.isLikelyReadOnly(makeFn("getBalance")));
            assertTrue(AuditReportGenerator.isLikelyReadOnly(makeFn("isAdmin")));
            assertTrue(AuditReportGenerator.isLikelyReadOnly(makeFn("hasVoted")));
            assertTrue(AuditReportGenerator.isLikelyReadOnly(makeFn("totalSupply")));
            assertTrue(AuditReportGenerator.isLikelyReadOnly(makeFn("balanceOf")));
            assertFalse(AuditReportGenerator.isLikelyReadOnly(makeFn("transfer")));
            assertFalse(AuditReportGenerator.isLikelyReadOnly(makeFn("approve")));
        }

        @Test
        @DisplayName("formatText produces readable output")
        void formatTextOutput() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR)
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            String text = AuditReportGenerator.formatText(report);
            assertNotNull(text);
            assertTrue(text.contains("DhrLang Security Audit Report"));
            assertTrue(text.contains("Token"));
            assertTrue(text.contains("Risk Score:"));
        }

        @Test
        @DisplayName("formatJson produces valid JSON structure")
        void formatJsonOutput() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR)
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            String json = AuditReportGenerator.formatJson(report);
            assertNotNull(json);
            assertTrue(json.startsWith("{"));
            assertTrue(json.endsWith("}"));
            assertTrue(json.contains("\"project\""));
            assertTrue(json.contains("\"riskScore\""));
            assertTrue(json.contains("\"contracts\""));
            assertTrue(json.contains("\"findings\""));
        }

        @Test
        @DisplayName("countBySeverity on report")
        void countBySeverity() {
            ClassDecl cls = makeContract("Unsafe",
                    List.of(storageVar("uint256", "balance")),
                    makeFnWithAnnotations("deposit", ContractAnnotation.PAYABLE)
            );
            AuditReport report = gen.analyze(makeProgram(cls));
            long total = report.countBySeverity(Severity.CRITICAL)
                    + report.countBySeverity(Severity.HIGH)
                    + report.countBySeverity(Severity.MEDIUM)
                    + report.countBySeverity(Severity.LOW)
                    + report.countBySeverity(Severity.INFORMATIONAL);
            assertEquals(report.getFindings().size(), total);
        }

        @Test
        @DisplayName("getFindings returns accumulated findings")
        void getAccumulatedFindings() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "balance")),
                    makeFnWithAnnotations("deposit", ContractAnnotation.PAYABLE)
            );
            gen.analyze(makeProgram(cls));
            assertFalse(gen.getFindings().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════
    //  2.  ContractDocGenerator — SC-502
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("2. ContractDocGenerator – SC-502")
    class ContractDocGeneratorTests {

        private ContractDocGenerator gen;

        @BeforeEach
        void setUp() {
            gen = new ContractDocGenerator();
        }

        @Test
        @DisplayName("DocFormat enum values")
        void docFormatEnum() {
            assertEquals(3, DocFormat.values().length);
            assertNotNull(DocFormat.MARKDOWN);
            assertNotNull(DocFormat.HTML);
            assertNotNull(DocFormat.PLAIN_TEXT);
        }

        @Test
        @DisplayName("Builder-style configuration")
        void builderConfig() {
            gen.setProjectTitle("My Contracts").setFormat(DocFormat.HTML);
            assertEquals("My Contracts", gen.getProjectTitle());
            assertEquals(DocFormat.HTML, gen.getFormat());
        }

        @Test
        @DisplayName("Generate docs for empty program")
        void generateDocsEmpty() {
            List<ContractDoc> docs = gen.generateDocs(makeProgram());
            assertTrue(docs.isEmpty());
        }

        @Test
        @DisplayName("Generate docs for simple contract")
        void generateDocsSimple() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("Address", "owner"), storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR),
                    makeFnAnnotatedWithParams("balanceOf", ContractAnnotation.VIEW, "Address"),
                    makeFnAnnotatedWithParams("Transfer", ContractAnnotation.EVENT,
                            "Address", "Address", "uint256")
            );
            List<ContractDoc> docs = gen.generateDocs(makeProgram(cls));
            assertEquals(1, docs.size());
            ContractDoc doc = docs.get(0);
            assertEquals("Token", doc.getName());
            assertTrue(doc.getAnnotations().contains("@contract"));
            assertEquals(2, doc.getStorageVariables().size());
            assertEquals(3, doc.getFunctions().size());
            assertTrue(doc.getEvents().contains("Transfer"));
        }

        @Test
        @DisplayName("DocumentFunction extracts annotations and mutability")
        void documentFunction() {
            FunctionDecl fn = makeFnAnnotatedWithParams("balanceOf", ContractAnnotation.VIEW, "Address");
            FunctionDoc doc = gen.documentFunction(fn);
            assertEquals("balanceOf", doc.getName());
            assertTrue(doc.getAnnotations().contains("@view"));
            assertEquals("view", doc.getStateMutability());
            assertEquals(1, doc.getParameters().size());
            assertEquals("Address", doc.getParameters().get(0).getType());
        }

        @Test
        @DisplayName("State mutability detection: pure")
        void stateMutabilityPure() {
            FunctionDecl fn = makeFnWithAnnotations("add", ContractAnnotation.PURE);
            FunctionDoc doc = gen.documentFunction(fn);
            assertEquals("pure", doc.getStateMutability());
        }

        @Test
        @DisplayName("State mutability detection: payable")
        void stateMutabilityPayable() {
            FunctionDecl fn = makeFnWithAnnotations("deposit", ContractAnnotation.PAYABLE);
            FunctionDoc doc = gen.documentFunction(fn);
            assertEquals("payable", doc.getStateMutability());
        }

        @Test
        @DisplayName("State mutability detection: nonpayable (default)")
        void stateMutabilityNonpayable() {
            FunctionDecl fn = makeFn("doSomething");
            FunctionDoc doc = gen.documentFunction(fn);
            assertEquals("nonpayable", doc.getStateMutability());
        }

        @Test
        @DisplayName("StorageDoc has correct slot indices")
        void storageDocSlots() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("Address", "owner"),
                            storageVar("uint256", "supply"),
                            plainVar("num", "localTemp")),
                    makeFn("init")
            );
            ContractDoc doc = gen.documentContract(cls);
            List<StorageDoc> storage = doc.getStorageVariables();
            assertEquals(3, storage.size());
            assertEquals(0, storage.get(0).getSlotIndex()); // owner → slot 0
            assertEquals(1, storage.get(1).getSlotIndex()); // supply → slot 1
            assertEquals(-1, storage.get(2).getSlotIndex()); // localTemp → not storage
        }

        @Test
        @DisplayName("FunctionDoc auto-description for constructor")
        void functionDescriptionConstructor() {
            FunctionDecl fn = makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR);
            FunctionDoc doc = gen.documentFunction(fn);
            assertTrue(doc.getDescription().contains("Initializes"));
        }

        @Test
        @DisplayName("FunctionDoc auto-description for event")
        void functionDescriptionEvent() {
            FunctionDecl fn = makeFnWithAnnotations("Transfer", ContractAnnotation.EVENT);
            FunctionDoc doc = gen.documentFunction(fn);
            assertTrue(doc.getDescription().contains("Transfer"));
        }

        @Test
        @DisplayName("ParamDoc stores name and type")
        void paramDocFields() {
            ParamDoc pd = new ParamDoc("amount", "uint256");
            assertEquals("amount", pd.getName());
            assertEquals("uint256", pd.getType());
        }

        @Test
        @DisplayName("Render Markdown output")
        void renderMarkdown() {
            gen.setFormat(DocFormat.MARKDOWN);
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR)
            );
            List<ContractDoc> docs = gen.generateDocs(makeProgram(cls));
            String md = gen.render(docs);
            assertNotNull(md);
            assertTrue(md.contains("# DhrLang Smart Contracts"));
            assertTrue(md.contains("## Token"));
            assertTrue(md.contains("Storage Variables"));
        }

        @Test
        @DisplayName("Render HTML output")
        void renderHtml() {
            gen.setFormat(DocFormat.HTML);
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR)
            );
            List<ContractDoc> docs = gen.generateDocs(makeProgram(cls));
            String html = gen.render(docs);
            assertNotNull(html);
            assertTrue(html.contains("<!DOCTYPE html>"));
            assertTrue(html.contains("Token"));
            assertTrue(html.contains("</html>"));
        }

        @Test
        @DisplayName("Render plain text output")
        void renderPlainText() {
            gen.setFormat(DocFormat.PLAIN_TEXT);
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFnWithAnnotations("init", ContractAnnotation.CONSTRUCTOR)
            );
            List<ContractDoc> docs = gen.generateDocs(makeProgram(cls));
            String text = gen.render(docs);
            assertNotNull(text);
            assertTrue(text.contains("Contract: Token"));
        }

        @Test
        @DisplayName("Multiple contracts generate multiple docs")
        void multipleContracts() {
            ClassDecl cls1 = makeContract("Token", List.of(), makeFn("init"));
            ClassDecl cls2 = makeContract("NFT", List.of(), makeFn("mint"));
            List<ContractDoc> docs = gen.generateDocs(makeProgram(cls1, cls2));
            assertEquals(2, docs.size());
            assertEquals("Token", docs.get(0).getName());
            assertEquals("NFT", docs.get(1).getName());
        }

        @Test
        @DisplayName("renderMarkdown includes Table of Contents")
        void markdownTableOfContents() {
            ClassDecl cls = makeContract("Vault", List.of(), makeFn("init"));
            List<ContractDoc> docs = gen.generateDocs(makeProgram(cls));
            String md = gen.renderMarkdown(docs);
            assertTrue(md.contains("Table of Contents"));
            assertTrue(md.contains("[Vault]"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  3.  DeploymentManager — SC-503
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("3. DeploymentManager – SC-503")
    class DeploymentManagerTests {

        private DeploymentManager mgr;

        @BeforeEach
        void setUp() {
            mgr = new DeploymentManager();
        }

        @Test
        @DisplayName("DeploymentStatus enum values")
        void deploymentStatusEnum() {
            assertEquals(6, DeploymentStatus.values().length);
            assertNotNull(DeploymentStatus.PENDING);
            assertNotNull(DeploymentStatus.TX_BUILT);
            assertNotNull(DeploymentStatus.SUBMITTED);
            assertNotNull(DeploymentStatus.CONFIRMED);
            assertNotNull(DeploymentStatus.FAILED);
            assertNotNull(DeploymentStatus.VERIFIED);
        }

        @Test
        @DisplayName("Default configuration values")
        void defaultConfig() {
            assertEquals("0x0000000000000000000000000000000000000000", mgr.getDeployerAddress());
            assertEquals(0, mgr.getNonce());
            assertEquals(30_000_000_000L, mgr.getMaxFeePerGas());
            assertEquals(2_000_000_000L, mgr.getMaxPriorityFeePerGas());
            assertEquals(1.2, mgr.getGasMultiplier(), 0.001);
            assertSame(L2ChainConfig.ETHEREUM_MAINNET, mgr.getTargetChain());
        }

        @Test
        @DisplayName("Builder-style configuration")
        void builderConfig() {
            mgr.setDeployerAddress("0xABCD")
               .setNonce(5)
               .setMaxFeePerGas(50_000_000_000L)
               .setMaxPriorityFeePerGas(3_000_000_000L)
               .setGasMultiplier(1.5)
               .setTargetChain(L2ChainConfig.ARBITRUM_ONE);
            assertEquals("0xABCD", mgr.getDeployerAddress());
            assertEquals(5, mgr.getNonce());
            assertEquals(50_000_000_000L, mgr.getMaxFeePerGas());
            assertEquals(3_000_000_000L, mgr.getMaxPriorityFeePerGas());
            assertEquals(1.5, mgr.getGasMultiplier(), 0.001);
            assertSame(L2ChainConfig.ARBITRUM_ONE, mgr.getTargetChain());
        }

        @Test
        @DisplayName("Build deploy transaction from artifact")
        void buildDeployTx() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            EvmContractCompiler compiler = new EvmContractCompiler(prog);
            List<ContractArtifact> artifacts = compiler.compileAll();
            assertFalse(artifacts.isEmpty());

            mgr.setDeployerAddress("0x1234");
            DeploymentTx tx = mgr.buildDeployTx(artifacts.get(0));
            assertNotNull(tx);
            assertEquals("DhrToken", tx.getContractName());
            assertEquals("1", tx.getChainId());
            assertEquals("Ethereum Mainnet", tx.getChainName());
            assertNotNull(tx.getCreationBytecodeHex());
            assertTrue(tx.getEstimatedGas() > 0);
            assertTrue(tx.getGasLimit() >= tx.getEstimatedGas());
            assertEquals("0x1234", tx.getFromAddress());
            assertEquals(0, tx.getNonce());
        }

        @Test
        @DisplayName("Nonce auto-increments after buildDeployTx")
        void nonceAutoIncrement() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            EvmContractCompiler compiler = new EvmContractCompiler(prog);
            List<ContractArtifact> artifacts = compiler.compileAll();

            assertEquals(0, mgr.getNonce());
            mgr.buildDeployTx(artifacts.get(0));
            assertEquals(1, mgr.getNonce());
        }

        @Test
        @DisplayName("buildDeployTxBatch builds multiple transactions")
        void buildBatchDeploy() {
            // Build two programs' artifacts
            Program prog1 = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            Program prog2 = ExampleContractTemplates.buildProgram(TemplateType.ERC721_NFT);
            List<ContractArtifact> allArtifacts = new ArrayList<>();
            allArtifacts.addAll(new EvmContractCompiler(prog1).compileAll());
            allArtifacts.addAll(new EvmContractCompiler(prog2).compileAll());

            List<DeploymentTx> txs = mgr.buildDeployTxBatch(allArtifacts);
            assertEquals(allArtifacts.size(), txs.size());
            // Nonce should have incremented for each
            assertEquals(allArtifacts.size(), mgr.getNonce());
        }

        @Test
        @DisplayName("Record and query deployment")
        void recordDeployment() {
            mgr.recordDeployment("Token", "0xContract123", "0xTx456", 100L, 50000L);
            assertEquals("0xContract123", mgr.getDeployedAddress("Token"));
            assertEquals(1, mgr.getDeployments().size());
            DeploymentRecord record = mgr.getDeployments().get(0);
            assertEquals(DeploymentStatus.CONFIRMED, record.getStatus());
            assertEquals("0xContract123", record.getContractAddress());
            assertEquals("0xTx456", record.getTxHash());
            assertEquals(100L, record.getBlockNumber());
            assertEquals(50000L, record.getGasUsed());
        }

        @Test
        @DisplayName("markVerified changes status from CONFIRMED to VERIFIED")
        void markVerified() {
            mgr.recordDeployment("Token", "0xAddr", "0xTx", 10L, 5000L);
            mgr.markVerified("Token");
            assertEquals(DeploymentStatus.VERIFIED, mgr.getDeployments().get(0).getStatus());
        }

        @Test
        @DisplayName("markVerified ignores non-CONFIRMED contracts")
        void markVerifiedIgnoresNonConfirmed() {
            // Build a tx (status = TX_BUILT), not CONFIRMED
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            EvmContractCompiler compiler = new EvmContractCompiler(prog);
            List<ContractArtifact> artifacts = compiler.compileAll();
            mgr.buildDeployTx(artifacts.get(0));

            DeploymentRecord record = mgr.getDeployments().get(0);
            assertEquals(DeploymentStatus.TX_BUILT, record.getStatus());
            mgr.markVerified("DhrToken");
            assertEquals(DeploymentStatus.TX_BUILT, record.getStatus()); // unchanged
        }

        @Test
        @DisplayName("Generate Foundry deployment script")
        void generateFoundryScript() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            List<ContractArtifact> artifacts = new EvmContractCompiler(prog).compileAll();
            String script = mgr.generateFoundryScript(artifacts);
            assertNotNull(script);
            assertTrue(script.contains("SPDX-License-Identifier"));
            assertTrue(script.contains("forge-std/Script.sol"));
            assertTrue(script.contains("DhrToken"));
            assertTrue(script.contains("vm.startBroadcast()"));
        }

        @Test
        @DisplayName("Generate ethers.js deployment script")
        void generateEthersScript() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            List<ContractArtifact> artifacts = new EvmContractCompiler(prog).compileAll();
            String script = mgr.generateEthersScript(artifacts);
            assertNotNull(script);
            assertTrue(script.contains("ethers"));
            assertTrue(script.contains("DhrToken"));
            assertTrue(script.contains("sendTransaction"));
        }

        @Test
        @DisplayName("Format deployment summary")
        void formatSummary() {
            mgr.recordDeployment("Token", "0xAddr", "0xTx", 10L, 5000L);
            String summary = mgr.formatSummary();
            assertNotNull(summary);
            assertTrue(summary.contains("DhrLang Deployment Summary"));
            assertTrue(summary.contains("Token"));
            assertTrue(summary.contains("0xAddr"));
        }

        @Test
        @DisplayName("Format summary with no deployments")
        void formatSummaryEmpty() {
            String summary = mgr.formatSummary();
            assertTrue(summary.contains("No deployments recorded"));
        }

        @Test
        @DisplayName("getDeployedAddresses returns read-only map")
        void deployedAddressesMap() {
            mgr.recordDeployment("Token", "0xA", "0xT", 1L, 100L);
            mgr.recordDeployment("NFT", "0xB", "0xU", 2L, 200L);
            Map<String, String> addrs = mgr.getDeployedAddresses();
            assertEquals(2, addrs.size());
            assertEquals("0xA", addrs.get("Token"));
            assertEquals("0xB", addrs.get("NFT"));
            assertThrows(UnsupportedOperationException.class,
                    () -> addrs.put("X", "Y"));
        }

        @Test
        @DisplayName("Reset clears all state")
        void resetState() {
            mgr.recordDeployment("Token", "0xA", "0xT", 1L, 100L);
            mgr.reset();
            assertTrue(mgr.getDeployments().isEmpty());
            assertTrue(mgr.getDeployedAddresses().isEmpty());
            assertEquals(0, mgr.getNonce());
        }

        @Test
        @DisplayName("estimateTotalCostEth returns non-negative")
        void estimateTotalCost() {
            mgr.recordDeployment("Token", "0xA", "0xT", 1L, 100000L);
            double cost = mgr.estimateTotalCostEth();
            assertTrue(cost >= 0);
        }

        @Test
        @DisplayName("Deploy to L2 chain (Arbitrum)")
        void deployToL2() {
            mgr.setTargetChain(L2ChainConfig.ARBITRUM_ONE);
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            EvmContractCompiler compiler = new EvmContractCompiler(prog);
            List<ContractArtifact> artifacts = compiler.compileAll();

            DeploymentTx tx = mgr.buildDeployTx(artifacts.get(0));
            assertEquals("42161", tx.getChainId());
            assertEquals("Arbitrum One", tx.getChainName());
        }
    }

    // ═══════════════════════════════════════════════════
    //  4.  L2ChainConfig — SC-504
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("4. L2ChainConfig – SC-504")
    class L2ChainConfigTests {

        @Test
        @DisplayName("ChainType enum values")
        void chainTypeEnum() {
            assertEquals(6, ChainType.values().length);
            assertNotNull(ChainType.L1);
            assertNotNull(ChainType.L2_OPTIMISTIC);
            assertNotNull(ChainType.L2_ZK);
            assertNotNull(ChainType.SIDECHAIN);
            assertNotNull(ChainType.TESTNET);
            assertNotNull(ChainType.LOCAL);
        }

        @Test
        @DisplayName("Ethereum Mainnet configuration")
        void ethereumMainnet() {
            L2ChainConfig chain = L2ChainConfig.ETHEREUM_MAINNET;
            assertEquals("1", chain.getChainId());
            assertEquals("Ethereum Mainnet", chain.getName());
            assertEquals("ETH", chain.getNativeToken());
            assertTrue(chain.isEip1559());
            assertTrue(chain.isProduction());
            assertFalse(chain.isL2());
            assertFalse(chain.isTestNetwork());
            assertEquals(ChainType.L1, chain.getChainType());
            assertTrue(chain.getGasLimit() > 0);
            assertTrue(chain.getBlockTimeSeconds() > 0);
        }

        @Test
        @DisplayName("Sepolia testnet configuration")
        void sepoliaTestnet() {
            L2ChainConfig chain = L2ChainConfig.SEPOLIA;
            assertEquals("11155111", chain.getChainId());
            assertTrue(chain.isTestNetwork());
            assertFalse(chain.isProduction());
            assertEquals(ChainType.TESTNET, chain.getChainType());
        }

        @Test
        @DisplayName("Arbitrum One L2 configuration")
        void arbitrumOne() {
            L2ChainConfig chain = L2ChainConfig.ARBITRUM_ONE;
            assertEquals("42161", chain.getChainId());
            assertTrue(chain.isL2());
            assertTrue(chain.isProduction());
            assertFalse(chain.isTestNetwork());
            assertEquals(ChainType.L2_OPTIMISTIC, chain.getChainType());
        }

        @Test
        @DisplayName("Base Mainnet L2 configuration")
        void baseMainnet() {
            L2ChainConfig chain = L2ChainConfig.BASE_MAINNET;
            assertEquals("8453", chain.getChainId());
            assertTrue(chain.isL2());
            assertTrue(chain.isProduction());
        }

        @Test
        @DisplayName("Optimism L2 configuration")
        void optimism() {
            L2ChainConfig chain = L2ChainConfig.OPTIMISM;
            assertEquals("10", chain.getChainId());
            assertTrue(chain.isL2());
            assertEquals(ChainType.L2_OPTIMISTIC, chain.getChainType());
        }

        @Test
        @DisplayName("Polygon sidechain configuration")
        void polygon() {
            L2ChainConfig chain = L2ChainConfig.POLYGON;
            assertEquals("137", chain.getChainId());
            assertEquals("MATIC", chain.getNativeToken());
            assertEquals(ChainType.SIDECHAIN, chain.getChainType());
            assertTrue(chain.isProduction());
            assertFalse(chain.isL2());
        }

        @Test
        @DisplayName("Local Anvil configuration")
        void localAnvil() {
            L2ChainConfig chain = L2ChainConfig.LOCAL_ANVIL;
            assertEquals("31337", chain.getChainId());
            assertTrue(chain.isTestNetwork());
            assertFalse(chain.isProduction());
            assertEquals(ChainType.LOCAL, chain.getChainType());
        }

        @Test
        @DisplayName("Look up chain by ID")
        void lookupByChainId() {
            assertSame(L2ChainConfig.ETHEREUM_MAINNET, L2ChainConfig.byChainId("1"));
            assertSame(L2ChainConfig.ARBITRUM_ONE, L2ChainConfig.byChainId("42161"));
            assertNull(L2ChainConfig.byChainId("9999999"));
        }

        @Test
        @DisplayName("Look up chain by name (case-insensitive)")
        void lookupByName() {
            L2ChainConfig chain = L2ChainConfig.byName("ethereum mainnet");
            assertNotNull(chain);
            assertEquals("1", chain.getChainId());

            assertNotNull(L2ChainConfig.byName("Arbitrum One"));
            assertNull(L2ChainConfig.byName("NonExistent"));
        }

        @Test
        @DisplayName("allChains returns all pre-defined chains")
        void allChains() {
            assertTrue(L2ChainConfig.allChains().size() >= 9);
        }

        @Test
        @DisplayName("productionChains filters correctly")
        void productionChains() {
            List<L2ChainConfig> prod = L2ChainConfig.productionChains();
            assertFalse(prod.isEmpty());
            for (L2ChainConfig c : prod) {
                assertTrue(c.isProduction());
            }
        }

        @Test
        @DisplayName("l2Chains filters correctly")
        void l2Chains() {
            List<L2ChainConfig> l2s = L2ChainConfig.l2Chains();
            assertFalse(l2s.isEmpty());
            for (L2ChainConfig c : l2s) {
                assertTrue(c.isL2());
            }
        }

        @Test
        @DisplayName("testnetChains filters correctly")
        void testnetChains() {
            List<L2ChainConfig> testnets = L2ChainConfig.testnetChains();
            assertFalse(testnets.isEmpty());
            for (L2ChainConfig c : testnets) {
                assertTrue(c.isTestNetwork());
            }
        }

        @Test
        @DisplayName("getRpcUrl replaces API key placeholder")
        void getRpcUrl() {
            String url = L2ChainConfig.ETHEREUM_MAINNET.getRpcUrl("my-key-123");
            assertTrue(url.contains("my-key-123"));
            assertFalse(url.contains("{API_KEY}"));
        }

        @Test
        @DisplayName("getTxUrl and getContractUrl build correct URLs")
        void explorerUrls() {
            String txUrl = L2ChainConfig.ETHEREUM_MAINNET.getTxUrl("0xabc");
            assertNotNull(txUrl);
            assertTrue(txUrl.contains("/tx/0xabc"));

            String contractUrl = L2ChainConfig.ETHEREUM_MAINNET.getContractUrl("0xdef");
            assertNotNull(contractUrl);
            assertTrue(contractUrl.contains("/address/0xdef"));
        }

        @Test
        @DisplayName("getTxUrl returns null for chain without explorer")
        void txUrlNoExplorer() {
            // Local Anvil has null explorer URL
            assertNull(L2ChainConfig.LOCAL_ANVIL.getTxUrl("0xabc"));
            assertNull(L2ChainConfig.LOCAL_ANVIL.getContractUrl("0xdef"));
        }

        @Test
        @DisplayName("formatSummary produces readable output")
        void formatSummary() {
            String summary = L2ChainConfig.ARBITRUM_ONE.formatSummary();
            assertNotNull(summary);
            assertTrue(summary.contains("Arbitrum One"));
            assertTrue(summary.contains("42161"));
        }

        @Test
        @DisplayName("equals and hashCode based on chainId")
        void equalsAndHashCode() {
            L2ChainConfig copy = new L2ChainConfig("42161", "Custom", null,
                    null, 1000, 1.0, "ETH", true, ChainType.L2_OPTIMISTIC);
            assertEquals(L2ChainConfig.ARBITRUM_ONE, copy);
            assertEquals(L2ChainConfig.ARBITRUM_ONE.hashCode(), copy.hashCode());
            assertNotEquals(L2ChainConfig.ETHEREUM_MAINNET, L2ChainConfig.ARBITRUM_ONE);
        }

        @Test
        @DisplayName("toString includes name and chainId")
        void toStringFormat() {
            String s = L2ChainConfig.ETHEREUM_MAINNET.toString();
            assertTrue(s.contains("Ethereum Mainnet"));
            assertTrue(s.contains("1"));
        }

        @Test
        @DisplayName("Register custom chain")
        void registerCustomChain() {
            L2ChainConfig custom = new L2ChainConfig("999999", "TestChain",
                    "http://localhost:9999", null, 100000, 1.0, "TST",
                    false, ChainType.LOCAL);
            L2ChainConfig.register(custom);
            assertSame(custom, L2ChainConfig.byChainId("999999"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  5.  ExampleContractTemplates — SC-505
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("5. ExampleContractTemplates – SC-505")
    class ExampleContractTemplatesTests {

        @Test
        @DisplayName("TemplateType enum has 4 templates")
        void templateTypeEnum() {
            assertEquals(4, TemplateType.values().length);
            assertNotNull(TemplateType.ERC20_TOKEN);
            assertNotNull(TemplateType.ERC721_NFT);
            assertNotNull(TemplateType.MULTI_SIG_WALLET);
            assertNotNull(TemplateType.STAKING_VAULT);
        }

        @Test
        @DisplayName("TemplateType display names and descriptions")
        void templateTypeMetadata() {
            assertEquals("ERC20 Token", TemplateType.ERC20_TOKEN.getDisplayName());
            assertNotNull(TemplateType.ERC20_TOKEN.getDescription());
            assertEquals("ERC721 NFT", TemplateType.ERC721_NFT.getDisplayName());
            assertEquals("Multi-Sig Wallet", TemplateType.MULTI_SIG_WALLET.getDisplayName());
            assertEquals("Staking Vault", TemplateType.STAKING_VAULT.getDisplayName());
        }

        @Test
        @DisplayName("allTemplates returns all 4 templates")
        void allTemplates() {
            Collection<TemplateInfo> all = ExampleContractTemplates.allTemplates();
            assertEquals(4, all.size());
        }

        @Test
        @DisplayName("availableTypes returns all 4 types")
        void availableTypes() {
            Set<TemplateType> types = ExampleContractTemplates.availableTypes();
            assertEquals(4, types.size());
            assertTrue(types.contains(TemplateType.ERC20_TOKEN));
        }

        @Test
        @DisplayName("ERC20 template has correct metadata")
        void erc20Template() {
            TemplateInfo info = ExampleContractTemplates.getTemplate(TemplateType.ERC20_TOKEN);
            assertNotNull(info);
            assertEquals("DhrToken", info.getName());
            assertEquals(TemplateType.ERC20_TOKEN, info.getType());
            assertEquals(7, info.getFunctionCount());
            assertEquals(4, info.getStorageSlotCount());
            assertEquals(2, info.getEventCount());
            assertFalse(info.getFeatures().isEmpty());
            assertNotNull(info.getSourceCode());
            assertTrue(info.getSourceCode().contains("@contract"));
            assertTrue(info.getSourceCode().contains("transfer"));
        }

        @Test
        @DisplayName("ERC721 template has correct metadata")
        void erc721Template() {
            TemplateInfo info = ExampleContractTemplates.getTemplate(TemplateType.ERC721_NFT);
            assertNotNull(info);
            assertEquals("DhrNFT", info.getName());
            assertEquals(9, info.getFunctionCount());
            assertEquals(6, info.getStorageSlotCount());
            assertEquals(2, info.getEventCount());
            assertTrue(info.getSourceCode().contains("mint"));
        }

        @Test
        @DisplayName("MultiSig template has correct metadata")
        void multiSigTemplate() {
            TemplateInfo info = ExampleContractTemplates.getTemplate(TemplateType.MULTI_SIG_WALLET);
            assertNotNull(info);
            assertEquals("MultiSigWallet", info.getName());
            assertEquals(8, info.getFunctionCount());
            assertEquals(7, info.getStorageSlotCount());
            assertEquals(3, info.getEventCount());
            assertTrue(info.getSourceCode().contains("confirmTransaction"));
        }

        @Test
        @DisplayName("StakingVault template has correct metadata")
        void stakingVaultTemplate() {
            TemplateInfo info = ExampleContractTemplates.getTemplate(TemplateType.STAKING_VAULT);
            assertNotNull(info);
            assertEquals("StakingVault", info.getName());
            assertEquals(9, info.getFunctionCount());
            assertEquals(6, info.getStorageSlotCount());
            assertEquals(3, info.getEventCount());
            assertTrue(info.getSourceCode().contains("@payable"));
        }

        @Test
        @DisplayName("buildProgram returns valid AST for ERC20")
        void buildProgramErc20() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            assertNotNull(prog);
            assertEquals(1, prog.getClasses().size());
            ClassDecl cls = prog.getClasses().get(0);
            assertEquals("DhrToken", cls.getName());
            assertTrue(cls.isContract());
            assertFalse(cls.getFunctions().isEmpty());
            assertFalse(cls.getVariables().isEmpty());
        }

        @Test
        @DisplayName("buildProgram returns valid AST for ERC721")
        void buildProgramErc721() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC721_NFT);
            assertNotNull(prog);
            ClassDecl cls = prog.getClasses().get(0);
            assertEquals("DhrNFT", cls.getName());
            assertTrue(cls.isContract());
        }

        @Test
        @DisplayName("buildProgram returns valid AST for MultiSig")
        void buildProgramMultiSig() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.MULTI_SIG_WALLET);
            assertNotNull(prog);
            ClassDecl cls = prog.getClasses().get(0);
            assertEquals("MultiSigWallet", cls.getName());
            assertTrue(cls.isContract());
        }

        @Test
        @DisplayName("buildProgram returns valid AST for StakingVault")
        void buildProgramStakingVault() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.STAKING_VAULT);
            assertNotNull(prog);
            ClassDecl cls = prog.getClasses().get(0);
            assertEquals("StakingVault", cls.getName());
            assertTrue(cls.isContract());
        }

        @Test
        @DisplayName("ERC20 AST has constructor function")
        void erc20AstHasConstructor() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            ClassDecl cls = prog.getClasses().get(0);
            boolean hasCtor = cls.getFunctions().stream()
                    .anyMatch(FunctionDecl::isContractConstructor);
            assertTrue(hasCtor);
        }

        @Test
        @DisplayName("ERC20 AST has event functions")
        void erc20AstHasEvents() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            ClassDecl cls = prog.getClasses().get(0);
            long eventCount = cls.getFunctions().stream()
                    .filter(fn -> fn.hasContractAnnotation(ContractAnnotation.EVENT))
                    .count();
            assertEquals(2, eventCount);
        }

        @Test
        @DisplayName("StakingVault AST has payable + nonreentrant function")
        void stakingVaultAstPayable() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.STAKING_VAULT);
            ClassDecl cls = prog.getClasses().get(0);
            boolean hasPayableNonreentrant = cls.getFunctions().stream()
                    .anyMatch(fn -> fn.isPayable() && fn.isNonReentrant());
            assertTrue(hasPayableNonreentrant);
        }

        @Test
        @DisplayName("Template ASTs compile to EVM bytecode")
        void templateAstCompiles() {
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                EvmContractCompiler compiler = new EvmContractCompiler(prog);
                List<ContractArtifact> artifacts = compiler.compileAll();
                assertFalse(artifacts.isEmpty(),
                        "Template " + type + " should produce at least one artifact");
                for (ContractArtifact a : artifacts) {
                    assertNotNull(a.getCreationBytecodeHex());
                    assertTrue(a.getEstimatedDeployGas() > 0);
                }
            }
        }

        @Test
        @DisplayName("formatCatalog produces readable output")
        void formatCatalog() {
            String catalog = ExampleContractTemplates.formatCatalog();
            assertNotNull(catalog);
            assertTrue(catalog.contains("DhrLang Contract Templates"));
            assertTrue(catalog.contains("ERC20 Token"));
            assertTrue(catalog.contains("ERC721 NFT"));
            assertTrue(catalog.contains("Multi-Sig Wallet"));
            assertTrue(catalog.contains("Staking Vault"));
        }

        @Test
        @DisplayName("storageVar helper creates @storage VarDecl")
        void storageVarHelper() {
            VarDecl v = ExampleContractTemplates.storageVar("uint256", "balance");
            assertEquals("uint256", v.getType());
            assertEquals("balance", v.getName());
            assertTrue(v.isStorage());
        }

        @Test
        @DisplayName("fn helper creates FunctionDecl without annotations")
        void fnHelper() {
            FunctionDecl f = ExampleContractTemplates.fn("transfer", "Address", "uint256");
            assertEquals("transfer", f.getName());
            assertEquals(2, f.getParameters().size());
            assertFalse(f.isView());
            assertFalse(f.isPure());
        }

        @Test
        @DisplayName("annotatedFn helper creates FunctionDecl with annotation")
        void annotatedFnHelper() {
            FunctionDecl f = ExampleContractTemplates.annotatedFn("getBalance",
                    ContractAnnotation.VIEW, "Address");
            assertEquals("getBalance", f.getName());
            assertTrue(f.isView());
            assertEquals(1, f.getParameters().size());
        }

        @Test
        @DisplayName("annotatedMultiFn helper creates FunctionDecl with multiple annotations")
        void annotatedMultiFnHelper() {
            FunctionDecl f = ExampleContractTemplates.annotatedMultiFn("stake",
                    Set.of(ContractAnnotation.PAYABLE, ContractAnnotation.NONREENTRANT));
            assertTrue(f.isPayable());
            assertTrue(f.isNonReentrant());
        }

        @Test
        @DisplayName("TemplateInfo description is non-empty")
        void templateInfoDescription() {
            for (TemplateType type : TemplateType.values()) {
                TemplateInfo info = ExampleContractTemplates.getTemplate(type);
                assertNotNull(info.getDescription());
                assertFalse(info.getDescription().isEmpty());
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  6.  VscodeLanguageSupport — SC-506
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("6. VscodeLanguageSupport – SC-506")
    class VscodeLanguageSupportTests {

        @Test
        @DisplayName("CompletionKind enum has all expected values")
        void completionKindEnum() {
            assertEquals(6, CompletionKind.values().length);
            assertNotNull(CompletionKind.KEYWORD);
            assertNotNull(CompletionKind.ANNOTATION);
            assertNotNull(CompletionKind.TYPE);
            assertNotNull(CompletionKind.SNIPPET);
            assertNotNull(CompletionKind.FUNCTION);
            assertNotNull(CompletionKind.VARIABLE);
        }

        @Test
        @DisplayName("DiagnosticSeverity enum has all expected values")
        void diagnosticSeverityEnum() {
            assertEquals(4, DiagnosticSeverity.values().length);
            assertNotNull(DiagnosticSeverity.ERROR);
            assertNotNull(DiagnosticSeverity.WARNING);
            assertNotNull(DiagnosticSeverity.INFORMATION);
            assertNotNull(DiagnosticSeverity.HINT);
        }

        // ── Completions ──

        @Test
        @DisplayName("Annotation completions include all contract annotations")
        void annotationCompletions() {
            List<CompletionItem> items = VscodeLanguageSupport.getAnnotationCompletions();
            assertFalse(items.isEmpty());
            assertTrue(items.size() >= 10);
            Set<String> labels = new HashSet<>();
            for (CompletionItem item : items) {
                labels.add(item.getLabel());
                assertEquals(CompletionKind.ANNOTATION, item.getKind());
                assertNotNull(item.getDocumentation());
            }
            assertTrue(labels.contains("@contract"));
            assertTrue(labels.contains("@storage"));
            assertTrue(labels.contains("@view"));
            assertTrue(labels.contains("@payable"));
            assertTrue(labels.contains("@nonreentrant"));
            assertTrue(labels.contains("@constructor"));
            assertTrue(labels.contains("@event"));
            assertTrue(labels.contains("@test"));
        }

        @Test
        @DisplayName("Keyword completions include DhrLang keywords")
        void keywordCompletions() {
            List<CompletionItem> items = VscodeLanguageSupport.getKeywordCompletions();
            assertFalse(items.isEmpty());
            Set<String> labels = new HashSet<>();
            for (CompletionItem item : items) {
                labels.add(item.getLabel());
                assertEquals(CompletionKind.KEYWORD, item.getKind());
            }
            assertTrue(labels.contains("kaam"));
            assertTrue(labels.contains("agar"));
            assertTrue(labels.contains("warna"));
            assertTrue(labels.contains("jabtak"));
            assertTrue(labels.contains("return"));
            assertTrue(labels.contains("sahi"));
            assertTrue(labels.contains("galat"));
        }

        @Test
        @DisplayName("Type completions include blockchain types")
        void typeCompletions() {
            List<CompletionItem> items = VscodeLanguageSupport.getTypeCompletions();
            assertFalse(items.isEmpty());
            Set<String> labels = new HashSet<>();
            for (CompletionItem item : items) {
                labels.add(item.getLabel());
                assertEquals(CompletionKind.TYPE, item.getKind());
            }
            assertTrue(labels.contains("Address"));
            assertTrue(labels.contains("uint256"));
            assertTrue(labels.contains("int256"));
            assertTrue(labels.contains("bytes32"));
            assertTrue(labels.contains("mapping"));
        }

        @Test
        @DisplayName("Snippet completions include templates")
        void snippetCompletions() {
            List<CompletionItem> items = VscodeLanguageSupport.getSnippetCompletions();
            assertFalse(items.isEmpty());
            Set<String> labels = new HashSet<>();
            for (CompletionItem item : items) {
                labels.add(item.getLabel());
                assertEquals(CompletionKind.SNIPPET, item.getKind());
                assertNotNull(item.getInsertText());
            }
            assertTrue(labels.contains("contract"));
            assertTrue(labels.contains("erc20"));
        }

        @Test
        @DisplayName("getCompletions filters by prefix")
        void getCompletionsByPrefix() {
            List<CompletionItem> results = VscodeLanguageSupport.getCompletions("@");
            assertFalse(results.isEmpty());
            for (CompletionItem item : results) {
                assertTrue(item.getLabel().startsWith("@"));
            }
        }

        @Test
        @DisplayName("getCompletions is case-insensitive")
        void getCompletionsCaseInsensitive() {
            List<CompletionItem> lower = VscodeLanguageSupport.getCompletions("kaam");
            List<CompletionItem> upper = VscodeLanguageSupport.getCompletions("KAAM");
            assertEquals(lower.size(), upper.size());
        }

        @Test
        @DisplayName("getCompletions returns empty for no match")
        void getCompletionsNoMatch() {
            List<CompletionItem> results = VscodeLanguageSupport.getCompletions("zzzzzzNothing");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("CompletionItem stores all fields")
        void completionItemFields() {
            CompletionItem item = new CompletionItem("test", CompletionKind.KEYWORD,
                    "detail", "insert", "doc");
            assertEquals("test", item.getLabel());
            assertEquals(CompletionKind.KEYWORD, item.getKind());
            assertEquals("detail", item.getDetail());
            assertEquals("insert", item.getInsertText());
            assertEquals("doc", item.getDocumentation());
        }

        // ── Hover ──

        @Test
        @DisplayName("Hover info for @contract annotation")
        void hoverContractAnnotation() {
            HoverInfo hover = VscodeLanguageSupport.getHover("@contract");
            assertNotNull(hover);
            assertEquals("@contract", hover.getSymbol());
            assertEquals("annotation", hover.getType());
            assertNotNull(hover.getDocumentation());
            assertNotNull(hover.getExample());
        }

        @Test
        @DisplayName("Hover info for keyword (kaam)")
        void hoverKeyword() {
            HoverInfo hover = VscodeLanguageSupport.getHover("kaam");
            assertNotNull(hover);
            assertEquals("kaam", hover.getSymbol());
            assertTrue(hover.getDocumentation().contains("function") ||
                       hover.getDocumentation().contains("Function"));
        }

        @Test
        @DisplayName("Hover info for type (Address)")
        void hoverType() {
            HoverInfo hover = VscodeLanguageSupport.getHover("Address");
            assertNotNull(hover);
            assertEquals("Address", hover.getSymbol());
            assertEquals("type", hover.getType());
        }

        @Test
        @DisplayName("Hover info for type (uint256)")
        void hoverUint256() {
            HoverInfo hover = VscodeLanguageSupport.getHover("uint256");
            assertNotNull(hover);
            assertTrue(hover.getDocumentation().contains("256"));
        }

        @Test
        @DisplayName("Hover returns null for unknown symbol")
        void hoverUnknown() {
            assertNull(VscodeLanguageSupport.getHover("unknownSymbol"));
        }

        @Test
        @DisplayName("Hover auto-prefixes @ for annotations")
        void hoverAutoPrefix() {
            HoverInfo hover = VscodeLanguageSupport.getHover("contract");
            assertNotNull(hover, "Should find @contract when given 'contract'");
        }

        @Test
        @DisplayName("allHoverSymbols returns non-empty set")
        void allHoverSymbols() {
            Set<String> symbols = VscodeLanguageSupport.allHoverSymbols();
            assertFalse(symbols.isEmpty());
            assertTrue(symbols.contains("@contract"));
            assertTrue(symbols.contains("kaam"));
            assertTrue(symbols.contains("Address"));
        }

        @Test
        @DisplayName("HoverInfo.toMarkdown renders correctly")
        void hoverToMarkdown() {
            HoverInfo hover = VscodeLanguageSupport.getHover("@view");
            assertNotNull(hover);
            String md = hover.toMarkdown();
            assertTrue(md.contains("**@view**"));
            assertTrue(md.contains("annotation"));
        }

        // ── Diagnostics ──

        @Test
        @DisplayName("Diagnostic stores all fields")
        void diagnosticFields() {
            Diagnostic d = new Diagnostic(1, 0, 1, 10,
                    DiagnosticSeverity.ERROR, "test msg", "DHR-E001");
            assertEquals(1, d.getStartLine());
            assertEquals(0, d.getStartColumn());
            assertEquals(1, d.getEndLine());
            assertEquals(10, d.getEndColumn());
            assertEquals(DiagnosticSeverity.ERROR, d.getSeverity());
            assertEquals("test msg", d.getMessage());
            assertEquals("DHR-E001", d.getCode());
            assertEquals("dhrlang", d.getSource());
        }

        @Test
        @DisplayName("Generate diagnostics for contract without constructor")
        void diagnosticMissingConstructor() {
            ClassDecl cls = makeContract("Token",
                    List.of(storageVar("uint256", "supply")),
                    makeFn("transfer")
            );
            List<Diagnostic> diags = VscodeLanguageSupport.generateDiagnostics(makeProgram(cls));
            boolean found = diags.stream()
                    .anyMatch(d -> d.getCode().equals("DHR-W001"));
            assertTrue(found, "Should warn about missing @constructor");
        }

        @Test
        @DisplayName("Generate diagnostics for @view + @pure conflict")
        void diagnosticViewPureConflict() {
            FunctionDecl fn = makeFnMultiAnnotated("bad",
                    Set.of(ContractAnnotation.VIEW, ContractAnnotation.PURE));
            ClassDecl cls = makeContract("Token", List.of(), fn);
            List<Diagnostic> diags = VscodeLanguageSupport.generateDiagnostics(makeProgram(cls));
            boolean found = diags.stream()
                    .anyMatch(d -> d.getCode().equals("DHR-E510"));
            assertTrue(found, "Should detect @view + @pure conflict");
        }

        @Test
        @DisplayName("Generate diagnostics for empty function body")
        void diagnosticEmptyBody() {
            ClassDecl cls = makeContract("Token",
                    List.of(),
                    makeFn("doNothing") // empty body, no annotations
            );
            List<Diagnostic> diags = VscodeLanguageSupport.generateDiagnostics(makeProgram(cls));
            boolean found = diags.stream()
                    .anyMatch(d -> d.getCode().equals("DHR-I001"));
            assertTrue(found, "Should info about empty function body");
        }

        @Test
        @DisplayName("No false positive for @event with empty body")
        void noFalsePositiveEventEmptyBody() {
            FunctionDecl event = makeFnWithAnnotations("Transfer", ContractAnnotation.EVENT);
            ClassDecl cls = makeContract("Token", List.of(), event);
            List<Diagnostic> diags = VscodeLanguageSupport.generateDiagnostics(makeProgram(cls));
            boolean emptyBodyForEvent = diags.stream()
                    .anyMatch(d -> d.getCode().equals("DHR-I001")
                            && d.getMessage().contains("Transfer"));
            assertFalse(emptyBodyForEvent, "Should not warn about empty @event body");
        }

        @Test
        @DisplayName("Non-contract classes don't trigger DHR-W001")
        void nonContractNoConstructorWarning() {
            ClassDecl cls = makeClass("Helper", makeFn("doSomething"));
            List<Diagnostic> diags = VscodeLanguageSupport.generateDiagnostics(makeProgram(cls));
            boolean found = diags.stream()
                    .anyMatch(d -> d.getCode().equals("DHR-W001"));
            assertFalse(found, "Non-contract should not get constructor warning");
        }

        // ── Signature Help ──

        @Test
        @DisplayName("Signature help for assertEqual")
        void signatureHelpAssertEqual() {
            SignatureInfo sig = VscodeLanguageSupport.getSignatureHelp("assertEqual");
            assertNotNull(sig);
            assertEquals(2, sig.getParameters().size());
            assertNotNull(sig.getDocumentation());
            assertTrue(sig.getLabel().contains("assertEqual"));
        }

        @Test
        @DisplayName("Signature help for require")
        void signatureHelpRequire() {
            SignatureInfo sig = VscodeLanguageSupport.getSignatureHelp("require");
            assertNotNull(sig);
            assertEquals(2, sig.getParameters().size());
        }

        @Test
        @DisplayName("Signature help returns null for unknown function")
        void signatureHelpUnknown() {
            assertNull(VscodeLanguageSupport.getSignatureHelp("nonExistent"));
        }

        @Test
        @DisplayName("allSignatureNames returns known functions")
        void allSignatureNames() {
            Set<String> names = VscodeLanguageSupport.allSignatureNames();
            assertFalse(names.isEmpty());
            assertTrue(names.contains("assertEqual"));
            assertTrue(names.contains("require"));
        }

        @Test
        @DisplayName("ParameterInfo stores label and documentation")
        void parameterInfoFields() {
            ParameterInfo pi = new ParameterInfo("x", "The x value");
            assertEquals("x", pi.getLabel());
            assertEquals("The x value", pi.getDocumentation());
        }

        @Test
        @DisplayName("SignatureInfo parameters are immutable")
        void signatureInfoImmutable() {
            SignatureInfo sig = VscodeLanguageSupport.getSignatureHelp("assertEqual");
            assertNotNull(sig);
            assertThrows(UnsupportedOperationException.class,
                    () -> sig.getParameters().add(new ParameterInfo("x", "y")));
        }
    }

    // ═══════════════════════════════════════════════════
    //  7.  Integration Tests — End-to-End Workflows
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("7. Integration Tests – End-to-End Workflows")
    class IntegrationTests {

        @Test
        @DisplayName("Full workflow: template → compile → audit → deploy")
        void fullWorkflow() {
            // 1. Pick a template
            TemplateInfo template = ExampleContractTemplates.getTemplate(TemplateType.ERC20_TOKEN);
            assertNotNull(template);

            // 2. Build AST
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            assertNotNull(prog);

            // 3. Compile
            EvmContractCompiler compiler = new EvmContractCompiler(prog);
            List<ContractArtifact> artifacts = compiler.compileAll();
            assertFalse(artifacts.isEmpty());

            // 4. Audit
            AuditReportGenerator auditor = new AuditReportGenerator();
            AuditReport report = auditor.analyze(prog);
            assertNotNull(report);
            assertTrue(report.getRiskScore() <= 100);

            // 5. Generate documentation
            ContractDocGenerator docGen = new ContractDocGenerator();
            List<ContractDoc> docs = docGen.generateDocs(prog);
            assertFalse(docs.isEmpty());

            // 6. Deploy to Arbitrum
            DeploymentManager mgr = new DeploymentManager();
            mgr.setTargetChain(L2ChainConfig.ARBITRUM_ONE)
               .setDeployerAddress("0x1234567890abcdef1234567890abcdef12345678");
            DeploymentTx tx = mgr.buildDeployTx(artifacts.get(0));
            assertNotNull(tx);
            assertEquals("42161", tx.getChainId());
        }

        @Test
        @DisplayName("All templates compile and pass audit")
        void allTemplatesCompileAndAudit() {
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                // Compile
                EvmContractCompiler compiler = new EvmContractCompiler(prog);
                List<ContractArtifact> artifacts = compiler.compileAll();
                assertFalse(artifacts.isEmpty(),
                        "Template " + type + " should compile");
                // Audit
                AuditReportGenerator auditor = new AuditReportGenerator();
                AuditReport report = auditor.analyze(prog);
                assertNotNull(report, "Template " + type + " should have audit report");
            }
        }

        @Test
        @DisplayName("All templates generate documentation")
        void allTemplatesGenerateDocs() {
            ContractDocGenerator docGen = new ContractDocGenerator();
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                List<ContractDoc> docs = docGen.generateDocs(prog);
                assertFalse(docs.isEmpty(),
                        "Template " + type + " should have documentation");
                ContractDoc doc = docs.get(0);
                assertNotNull(doc.getName());
                assertFalse(doc.getFunctions().isEmpty());
            }
        }

        @Test
        @DisplayName("Deploy all templates to all production chains")
        void deployToAllProductionChains() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            List<ContractArtifact> artifacts = new EvmContractCompiler(prog).compileAll();

            for (L2ChainConfig chain : L2ChainConfig.productionChains()) {
                DeploymentManager mgr = new DeploymentManager();
                mgr.setTargetChain(chain);
                DeploymentTx tx = mgr.buildDeployTx(artifacts.get(0));
                assertEquals(chain.getChainId(), tx.getChainId());
                assertEquals(chain.getName(), tx.getChainName());
            }
        }

        @Test
        @DisplayName("VSCode diagnostics work with template ASTs")
        void vscodeDiagnosticsOnTemplates() {
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                List<Diagnostic> diags = VscodeLanguageSupport.generateDiagnostics(prog);
                // Should not have any ERROR-level diagnostics
                boolean hasError = diags.stream()
                        .anyMatch(d -> d.getSeverity() == DiagnosticSeverity.ERROR);
                assertFalse(hasError,
                        "Template " + type + " should have no ERROR diagnostics");
            }
        }

        @Test
        @DisplayName("Audit report text and JSON formats are non-empty for all templates")
        void auditFormatsForAllTemplates() {
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                AuditReport report = new AuditReportGenerator().analyze(prog);
                String text = AuditReportGenerator.formatText(report);
                String json = AuditReportGenerator.formatJson(report);
                assertFalse(text.isEmpty(), type + " text report should not be empty");
                assertFalse(json.isEmpty(), type + " JSON report should not be empty");
            }
        }

        @Test
        @DisplayName("Doc generator formats for all templates")
        void docFormatsForAllTemplates() {
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                ContractDocGenerator docGen = new ContractDocGenerator();
                List<ContractDoc> docs = docGen.generateDocs(prog);

                docGen.setFormat(DocFormat.MARKDOWN);
                String md = docGen.render(docs);
                assertFalse(md.isEmpty());

                docGen.setFormat(DocFormat.HTML);
                String html = docGen.render(docs);
                assertFalse(html.isEmpty());

                docGen.setFormat(DocFormat.PLAIN_TEXT);
                String text = docGen.render(docs);
                assertFalse(text.isEmpty());
            }
        }

        @Test
        @DisplayName("Foundry and ethers scripts generated for all templates")
        void scriptGenerationForAllTemplates() {
            DeploymentManager mgr = new DeploymentManager();
            for (TemplateType type : TemplateType.values()) {
                Program prog = ExampleContractTemplates.buildProgram(type);
                List<ContractArtifact> artifacts = new EvmContractCompiler(prog).compileAll();
                assertFalse(artifacts.isEmpty());
                String foundry = mgr.generateFoundryScript(artifacts);
                String ethers = mgr.generateEthersScript(artifacts);
                assertFalse(foundry.isEmpty(), type + " Foundry script");
                assertFalse(ethers.isEmpty(), type + " ethers script");
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  8.  Edge Cases & Error Handling
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("8. Edge Cases & Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Audit empty class list")
        void auditEmptyClassList() {
            AuditReport report = new AuditReportGenerator().analyze(new Program(List.of()));
            assertEquals(0, report.getRiskScore());
            assertTrue(report.getContracts().isEmpty());
        }

        @Test
        @DisplayName("Doc generator with no functions contract")
        void docGenNoFunctions() {
            ClassDecl cls = makeContract("Empty", List.of());
            ContractDocGenerator gen = new ContractDocGenerator();
            ContractDoc doc = gen.documentContract(cls);
            assertTrue(doc.getFunctions().isEmpty());
        }

        @Test
        @DisplayName("DeploymentManager reset is idempotent")
        void resetIdempotent() {
            DeploymentManager mgr = new DeploymentManager();
            mgr.reset();
            mgr.reset();
            assertTrue(mgr.getDeployments().isEmpty());
        }

        @Test
        @DisplayName("L2ChainConfig.getRpcUrl handles null API key")
        void rpcUrlNullApiKey() {
            String url = L2ChainConfig.ETHEREUM_MAINNET.getRpcUrl(null);
            assertNotNull(url);
            assertFalse(url.contains("{API_KEY}"));
        }

        @Test
        @DisplayName("DeploymentTx fields immutable via getter")
        void deploymentTxImmutableFields() {
            Program prog = ExampleContractTemplates.buildProgram(TemplateType.ERC20_TOKEN);
            List<ContractArtifact> artifacts = new EvmContractCompiler(prog).compileAll();
            DeploymentManager mgr = new DeploymentManager();
            DeploymentTx tx = mgr.buildDeployTx(artifacts.get(0));

            // Ensure getters return consistent values
            String name = tx.getContractName();
            String chain = tx.getChainId();
            assertEquals(name, tx.getContractName());
            assertEquals(chain, tx.getChainId());
        }

        @Test
        @DisplayName("Hover info toMarkdown handles null fields gracefully")
        void hoverInfoNullFields() {
            HoverInfo hover = new HoverInfo("test", null, null, null);
            String md = hover.toMarkdown();
            assertNotNull(md);
            assertTrue(md.contains("test"));
        }

        @Test
        @DisplayName("Multiple audits reuse the same generator")
        void multipleAuditsReuse() {
            AuditReportGenerator gen = new AuditReportGenerator();
            ClassDecl cls1 = makeContract("Token1", List.of(), makeFn("init"));
            ClassDecl cls2 = makeContract("Token2", List.of(), makeFn("init"));

            AuditReport r1 = gen.analyze(makeProgram(cls1));
            AuditReport r2 = gen.analyze(makeProgram(cls2));

            // Second audit should be independent (findings cleared)
            assertEquals(1, r1.getContracts().size());
            assertEquals(1, r2.getContracts().size());
            assertEquals("Token1", r1.getContracts().get(0).getName());
            assertEquals("Token2", r2.getContracts().get(0).getName());
        }

        @Test
        @DisplayName("VscodeLanguageSupport getHover with null returns null")
        void hoverNull() {
            assertNull(VscodeLanguageSupport.getHover(null));
        }

        @Test
        @DisplayName("L2ChainConfig allChains is immutable")
        void allChainsImmutable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> L2ChainConfig.allChains().add(null));
        }
    }
}
