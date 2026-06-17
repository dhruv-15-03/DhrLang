package dhrlang;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.error.ErrorMessages;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.RuntimeError;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.parser.ParseException;
import dhrlang.typechecker.TypeChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static ErrorReporter errorReporter = new ErrorReporter();

    public static void main(String[] args) {
        CliOptions options = parseArgs(args);
        if (options.showHelp) { printHelp(); return; }
        if (options.showVersion) { printVersion(); return; }
        if (options.lspMode) {
            try { dhrlang.lsp.DhrLangLspServer.startLsp(); } catch (Exception e) { System.exit(1); }
            return;
        }

        // Handle "contract" subcommand: wallet/networks don't need a .dhr file
        if (options.contractMode) {
            var bcOpts = dhrlang.deploy.BlockchainCLI.parseArgs(options.contractArgs, 1);
            if ("wallet".equals(bcOpts.subcommand) || "networks".equals(bcOpts.subcommand)) {
                dhrlang.deploy.BlockchainCLI.execute(null, null, bcOpts, errorReporter);
                return;
            }
        }

        String filePath = options.filePath != null ? options.filePath : "input/sample.dhr";
        String sourceCode;
        try {
            sourceCode = Files.readString(Path.of(filePath));
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath);
            System.exit(1);
            return;
        }
        errorReporter.setSource(filePath, sourceCode);
        PhaseTimings timings = executePipeline(sourceCode, options);

        if (errorReporter.hasErrors()) {
            if(options.jsonMode){
                System.out.println(buildJsonOutput(options, timings));
                System.exit(65);
            }
            System.err.println();
            System.err.println("\u001B[91m╔══════════════════════════════════════════════════════════════╗\u001B[0m");
            System.err.println("\u001B[91m║                    COMPILATION FAILED                       ║\u001B[0m");
            System.err.println("\u001B[91m╚══════════════════════════════════════════════════════════════╝\u001B[0m");
            System.err.println();
            int errorCount = errorReporter.getErrorCount();
            int warningCount = errorReporter.getWarningCount();
            if (errorCount > 0) {
                System.err.println("\u001B[91m❌ " + errorCount + " error" + (errorCount > 1 ? "s" : "") + " found:\u001B[0m");
                System.err.println();
            }
            errorReporter.printAllErrors();
            if (warningCount > 0) {
                System.err.println("\u001B[93m⚠️  " + warningCount + " warning" + (warningCount > 1 ? "s" : "") + " found:\u001B[0m");
                System.err.println();
                errorReporter.printAllWarnings();
            }
            System.exit(1);
        }

        if (errorReporter.hasWarnings()) {
            if(options.jsonMode){ System.out.println(buildJsonOutput(options, timings)); return; }
            System.err.println();
            System.err.println("\u001B[93m╔══════════════════════════════════════════════════════════════╗\u001B[0m");
            System.err.println("\u001B[93m║                        WARNINGS                             ║\u001B[0m");
            System.err.println("\u001B[93m╚══════════════════════════════════════════════════════════════╝\u001B[0m");
            System.err.println();
            errorReporter.printAllWarnings();
        }
        if(options.jsonMode && !errorReporter.hasErrors()){
            // Always emit JSON when --json is set (even if no errors/warnings)
            System.out.println(buildJsonOutput(options, timings));
        } else if(options.timeMode && !options.jsonMode){
            printTimings(timings);
        }
    }

    private static String buildJsonOutput(CliOptions opts, PhaseTimings timings){
        // Always emit schemaVersion and timings object (timings may be zero if early error)
        if(timings == null) timings = new PhaseTimings();
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"schemaVersion\":1");
        sb.append(",\"timings\":{");
        sb.append("\"lexMs\":").append(timings.lexMs).append(',');
        sb.append("\"parseMs\":").append(timings.parseMs).append(',');
        sb.append("\"typeMs\":").append(timings.typeMs).append(',');
        sb.append("\"execMs\":").append(timings.execMs).append(',');
        sb.append("\"totalMs\":").append(timings.totalMs).append('}');
        String core = errorReporter.toJson();
        if(core.startsWith("{")) core = core.substring(1);
        sb.append(',').append(core);
        return sb.toString();
    }

    private static void printTimings(PhaseTimings t){
        System.out.println("Timings (ms):");
        System.out.println("  lex   : " + t.lexMs);
        System.out.println("  parse : " + t.parseMs);
        System.out.println("  type  : " + t.typeMs);
        System.out.println("  exec  : " + t.execMs);
        System.out.println("  total : " + t.totalMs);
    }

    private static void printVersion() {
        // Version is embedded at build time via manifest Implementation-Version if available
        String version = Main.class.getPackage() != null ? Main.class.getPackage().getImplementationVersion() : null;
        if (version == null) version = "(development)";
        System.out.println("DhrLang version " + version);
    }

    private static void printHelp() {
        System.out.println("DhrLang - a compact statically typed language (num/duo/sab/kya/ek/kaam)\n");
        System.out.println("Usage: java -jar DhrLang.jar [options] <file.dhr>\n");
        System.out.println("Options:");
        System.out.println("  --help               Show this help and exit");
        System.out.println("  --version            Print version and exit");
        System.out.println("  --json               Emit diagnostics as JSON (errors/warnings)");
        System.out.println("  --time               Show phase timings (lex/parse/type/exec)");
        System.out.println("  --no-color           Disable ANSI colors in diagnostics");
        System.out.println("  --check              Type-check only (no execution)");
        System.out.println("  --backend=<ast|ir|bytecode>  Select execution backend");
        System.out.println("  --emit-ir            Dump IR to stdout (with --backend=ir)");
        System.out.println("  --emit-bc            Write bytecode to build/bytecode/Main.dbc");
        System.out.println();
        System.out.println("Smart Contract Tools:");
        System.out.println("  --compile-evm        Compile @contract classes to EVM bytecode");
        System.out.println("  --audit              Generate security audit report for contracts");
        System.out.println("  --docs               Generate documentation for @contract classes");
        System.out.println("  --deploy-script      Generate deployment script for compiled contracts");
        System.out.println("  --deploy-format=<foundry|ethers>  Deployment script format (default: foundry)");
        System.out.println("  --debug-evm          Interactive EVM bytecode debugger");
        System.out.println("  --output=<dir>       Output directory for artifacts (default: build/evm/)");
        System.out.println();
        System.out.println("Blockchain CLI (unified interface):");
        System.out.println("  contract compile     Compile @contract classes to EVM bytecode + ABI");
        System.out.println("  contract deploy      Build, sign, and deploy contracts to a network");
        System.out.println("  contract verify      Verify contract source on block explorer");
        System.out.println("  contract gas         Estimate deployment gas costs + ETH cost");
        System.out.println("  contract wallet      Manage wallet keys (create keystore, show address)");
        System.out.println("  contract networks    List all supported blockchain networks");
        System.out.println("  contract status      Check contract deployment status on-chain");
        System.out.println("  (use 'contract --help' for full subcommand documentation)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar DhrLang.jar input/sample.dhr");
        System.out.println("  java -jar DhrLang.jar --check myfile.dhr");
        System.out.println("  java -jar DhrLang.jar --compile-evm contract.dhr");
        System.out.println("  java -jar DhrLang.jar contract compile token.dhr");
        System.out.println("  java -jar DhrLang.jar contract deploy --network=sepolia token.dhr");
        System.out.println("  java -jar DhrLang.jar contract gas token.dhr");
        System.out.println("  java -jar DhrLang.jar contract verify --address=0x... token.dhr");
        System.out.println("  java -jar DhrLang.jar contract wallet create");
        System.out.println("  java -jar DhrLang.jar contract networks");
        System.out.println();
        System.out.println("If no file is provided, defaults to input/sample.dhr");
    }

    private static class CliOptions {
        boolean showHelp;
        boolean showVersion;
        boolean jsonMode;
        String filePath;
        boolean timeMode;
        boolean noColor;
        String backend = "ast"; // ast | ir | bytecode
        boolean emitIr;
        boolean emitBc;
        // --- New modes (Iteration 3-7 features) ---
        boolean checkOnly;       // --check: type-check only, no execution
        boolean compileEvm;      // --compile-evm: compile @contract classes to EVM bytecode
        boolean audit;           // --audit: security audit report
        boolean genDocs;         // --docs: generate contract documentation
        boolean deployScript;    // --deploy-script: generate deploy scripts
        String deployFormat = "foundry"; // foundry | ethers
        boolean debugEvm;        // --debug-evm: step through EVM bytecode
        boolean sarifMode;       // --sarif: output SARIF format (for --audit)
        boolean lspMode;         // --lsp: start Language Server Protocol server
        String outputDir;        // --output=<dir>: output directory for artifacts
        // --- Contract subcommand ---
        boolean contractMode;    // contract <subcommand>: blockchain operations
        String[] contractArgs;   // raw args for BlockchainCLI parsing
    }

    private static CliOptions parseArgs(String[] args) {
        CliOptions opts = new CliOptions();
        // Check for "contract" subcommand first
        if (args.length > 0 && "contract".equals(args[0])) {
            opts.contractMode = true;
            opts.contractArgs = args;
            // Extract file path from remaining args
            for (int i = 1; i < args.length; i++) {
                if (!args[i].startsWith("-") && args[i].endsWith(".dhr")) {
                    opts.filePath = args[i];
                }
            }
            return opts;
        }
        for (String a : args) {
            switch (a) {
                case "--help":
                case "-h":
                    opts.showHelp = true; break;
                case "--version":
                case "-v":
                    opts.showVersion = true; break;
                case "--json":
                    opts.jsonMode = true; break;
                case "--time":
                    opts.timeMode = true; break;
                case "--no-color":
                    opts.noColor = true; break;
                case "--emit-ir":
                    opts.emitIr = true; break;
                case "--emit-bc":
                    opts.emitBc = true; break;
                case "--check":
                    opts.checkOnly = true; break;
                case "--compile-evm":
                    opts.compileEvm = true; break;
                case "--audit":
                    opts.audit = true; break;
                case "--docs":
                    opts.genDocs = true; break;
                case "--deploy-script":
                    opts.deployScript = true; break;
                case "--debug-evm":
                    opts.debugEvm = true; break;
                case "--sarif":
                    opts.sarifMode = true; break;
                case "--lsp":
                    opts.lspMode = true; break;
                default:
                    // First non-flag is treated as file path
                    if (!a.startsWith("-")) {
                        opts.filePath = a;
                    } else if(a.startsWith("--backend=")) {
                        String val = a.substring("--backend=".length());
                        if(val.equals("ast") || val.equals("ir") || val.equals("bytecode")) {
                            opts.backend = val;
                        } else {
                            System.err.println("Unknown backend '"+val+"' (supported: ast, ir, bytecode)");
                            opts.showHelp = true;
                        }
                    } else if(a.startsWith("--output=")) {
                        opts.outputDir = a.substring("--output=".length());
                    } else if(a.startsWith("--deploy-format=")) {
                        String val = a.substring("--deploy-format=".length());
                        if(val.equals("foundry") || val.equals("ethers")) {
                            opts.deployFormat = val;
                        } else {
                            System.err.println("Unknown deploy format '"+val+"' (supported: foundry, ethers)");
                            opts.showHelp = true;
                        }
                    } else {
                        System.err.println("Unknown option: " + a);
                        opts.showHelp = true;
                    }
            }
        }
        return opts;
    }
    private static PhaseTimings executePipeline(String sourceCode, CliOptions opts){
        long tStart = System.nanoTime();
        errorReporter.setColorEnabled(!opts.noColor);
        PhaseTimings pt = new PhaseTimings();
        long s = System.nanoTime();
        Lexer lexer = new Lexer(sourceCode, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        pt.lexMs = msSince(s);
        if(errorReporter.hasErrors()){ pt.totalMs = msSince(tStart); return pt; }

        s = System.nanoTime();
        Parser parser = new Parser(tokens, errorReporter);
        Program program = null;
        try { program = parser.parse(); } catch (ParseException ignored) {}
        pt.parseMs = msSince(s);
        if(errorReporter.hasErrors()){ pt.totalMs = msSince(tStart); return pt; }

        s = System.nanoTime();
        TypeChecker typeChecker = new TypeChecker(errorReporter);
        typeChecker.check(program);
        pt.typeMs = msSince(s);

        // --audit / --sarif is an ANALYSIS mode: it must run even when the
        // type-checker or validator reported errors, because surfacing those
        // problems as findings is its entire purpose. (Lexer/parser failures
        // still short-circuit above, since findings need a usable AST.)
        if(opts.audit) {
            s = System.nanoTime();
            handleAudit(program, opts);
            pt.execMs = msSince(s);
            pt.totalMs = msSince(tStart);
            return pt;
        }

        if(errorReporter.hasErrors()){ pt.totalMs = msSince(tStart); return pt; }

        // --check mode: type-check passed, exit successfully
        if(opts.checkOnly) {
            if(!opts.jsonMode) {
                System.out.println("Type-check passed. No errors found.");
            }
            pt.totalMs = msSince(tStart);
            return pt;
        }

        // contract subcommand: route to BlockchainCLI
        if(opts.contractMode && opts.contractArgs != null) {
            s = System.nanoTime();
            var bcOpts = dhrlang.deploy.BlockchainCLI.parseArgs(opts.contractArgs, 1);
            dhrlang.deploy.BlockchainCLI.execute(program, sourceCode, bcOpts, errorReporter);
            pt.execMs = msSince(s);
            pt.totalMs = msSince(tStart);
            return pt;
        }

        // --compile-evm mode: compile @contract classes to EVM bytecode
        if(opts.compileEvm) {
            s = System.nanoTime();
            handleCompileEvm(program, opts);
            pt.execMs = msSince(s);
            pt.totalMs = msSince(tStart);
            return pt;
        }

        // --docs mode: generate contract documentation
        if(opts.genDocs) {
            s = System.nanoTime();
            handleDocs(program, opts);
            pt.execMs = msSince(s);
            pt.totalMs = msSince(tStart);
            return pt;
        }

        // --deploy-script mode: compile then generate deploy scripts
        if(opts.deployScript) {
            s = System.nanoTime();
            handleDeployScript(program, opts);
            pt.execMs = msSince(s);
            pt.totalMs = msSince(tStart);
            return pt;
        }

        // --debug-evm mode: compile then launch interactive debugger
        if(opts.debugEvm) {
            s = System.nanoTime();
            handleDebugEvm(program, opts);
            pt.execMs = msSince(s);
            pt.totalMs = msSince(tStart);
            return pt;
        }

        s = System.nanoTime();
        try {
            if("ir".equalsIgnoreCase(opts.backend)) {
                dhrlang.ir.AstToIrLowerer lowerer = new dhrlang.ir.AstToIrLowerer(errorReporter);
                dhrlang.ir.IrProgram irProgram = lowerer.lower(program);
                if(errorReporter.hasErrors()){
                    pt.execMs = msSince(s);
                    pt.totalMs = msSince(tStart);
                    return pt;
                }
                dhrlang.ir.opt.IrOptimizer.defaultPipeline().optimize(irProgram);
                if(opts.emitIr){
                    System.out.println(serializeIr(irProgram));
                }
                new dhrlang.ir.IrInterpreter().execute(irProgram);
            } else if("bytecode".equalsIgnoreCase(opts.backend)) {
                dhrlang.ir.AstToIrLowerer lowerer = new dhrlang.ir.AstToIrLowerer(errorReporter);
                dhrlang.ir.IrProgram irProgram = lowerer.lower(program);
                if(errorReporter.hasErrors()){
                    pt.execMs = msSince(s);
                    pt.totalMs = msSince(tStart);
                    return pt;
                }
                dhrlang.ir.opt.IrOptimizer.defaultPipeline().optimize(irProgram);
                dhrlang.bytecode.BytecodeWriter writer = new dhrlang.bytecode.BytecodeWriter();
                byte[] bc = writer.write(irProgram);
                if(opts.emitBc){
                    try{
                        java.nio.file.Path outPath = java.nio.file.Paths.get("build","bytecode","Main.dbc");
                        java.nio.file.Files.createDirectories(outPath.getParent());
                        java.nio.file.Files.write(outPath, bc);
                        System.out.println("[bytecode] wrote "+outPath.toAbsolutePath());
                    } catch(Exception ex){ System.err.println("Failed to write bytecode: "+ex); }
                }
                new dhrlang.bytecode.BytecodeVM().execute(bc);
            } else {
                Interpreter interpreter = new Interpreter();
                interpreter.execute(program);
            }
        } catch (dhrlang.interpreter.DhrRuntimeException e) {
                printRuntimeError(e, sourceCode);
            System.exit(2);
        } catch (RuntimeError e) {
                printSystemError(e);
            System.exit(2);
        }
        pt.execMs = msSince(s);
        pt.totalMs = msSince(tStart);
        return pt;
    }

    private static long msSince(long start){ return (System.nanoTime()-start)/1_000_000L; }

    // ═══════════════════════════════════════════════════════════════
    //  New CLI mode handlers (Iterations 3-7)
    // ═══════════════════════════════════════════════════════════════

    private static void handleCompileEvm(Program program, CliOptions opts) {
        try {
            var compiler = new dhrlang.evm.EvmContractCompiler(program, errorReporter);
            var artifacts = compiler.compileAll();

            if (artifacts.isEmpty()) {
                System.out.println("No @contract classes found in the source file.");
                System.out.println("Hint: Annotate your class with @contract to compile it to EVM bytecode.");
                System.out.println("  Example: @contract class MyToken { ... }");
                return;
            }

            String outDir = opts.outputDir != null ? opts.outputDir : "build/evm";
            java.nio.file.Path outPath = java.nio.file.Paths.get(outDir);
            Files.createDirectories(outPath);

            for (var artifact : artifacts) {
                String name = artifact.getContractName();

                // Write creation bytecode
                byte[] creation = artifact.getCreationBytecode();
                if (creation != null && creation.length > 0) {
                    Files.write(outPath.resolve(name + ".bin"), creation);
                }

                // Write runtime bytecode
                byte[] runtime = artifact.getRuntimeBytecode();
                if (runtime != null && runtime.length > 0) {
                    Files.write(outPath.resolve(name + ".runtime.bin"), runtime);
                }

                // Write ABI JSON
                String abi = artifact.getAbiJson();
                if (abi != null && !abi.isEmpty()) {
                    Files.writeString(outPath.resolve(name + ".abi.json"), abi);
                }

                long gasEstimate = artifact.getEstimatedDeployGas();
                System.out.println("Compiled: " + name);
                System.out.println("  Creation bytecode: " + (creation != null ? creation.length : 0) + " bytes");
                System.out.println("  Runtime bytecode:  " + (runtime != null ? runtime.length : 0) + " bytes");
                System.out.println("  Gas estimate:      " + gasEstimate);
                System.out.println("  Output:            " + outPath.resolve(name + ".bin"));
            }

            System.out.println("\n" + artifacts.size() + " contract(s) compiled to " + outPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("EVM compilation failed: " + e.getMessage());
            System.err.println("Hint: Ensure your file contains @contract annotated classes.");
            System.exit(2);
        }
    }

    private static void handleAudit(Program program, CliOptions opts) {
        try {
            var auditor = new dhrlang.production.AuditReportGenerator();
            String version = Main.class.getPackage() != null ? Main.class.getPackage().getImplementationVersion() : null;
            auditor.setProjectName(opts.filePath != null ? opts.filePath : "DhrLang Project");
            auditor.setCompilerVersion(version != null ? version : "dev");

            var report = auditor.analyze(program);

            // SARIF output for CI/CD integration (GitHub Code Scanning, Azure DevOps)
            if (opts.sarifMode) {
                String sarif = dhrlang.production.SarifFormatter.format(report, opts.filePath);
                System.out.println(sarif);
                if (opts.outputDir != null) {
                    java.nio.file.Path outPath = java.nio.file.Paths.get(opts.outputDir);
                    Files.createDirectories(outPath);
                    Files.writeString(outPath.resolve("audit-report.sarif"), sarif);
                    System.err.println("SARIF report written to: " + outPath.resolve("audit-report.sarif"));
                }
                // Audit is a terminal analysis action: succeed cleanly so a
                // non-empty findings list does not surface as a build failure.
                System.exit(0);
            }

            if (opts.jsonMode) {
                System.out.println(dhrlang.production.AuditReportGenerator.formatJson(report));
            } else {
                System.out.println(dhrlang.production.AuditReportGenerator.formatText(report));
            }

            // Optionally write to file
            if (opts.outputDir != null) {
                java.nio.file.Path outPath = java.nio.file.Paths.get(opts.outputDir);
                Files.createDirectories(outPath);
                String ext = opts.jsonMode ? ".audit.json" : ".audit.txt";
                String content = opts.jsonMode
                    ? dhrlang.production.AuditReportGenerator.formatJson(report)
                    : dhrlang.production.AuditReportGenerator.formatText(report);
                Files.writeString(outPath.resolve("audit-report" + ext), content);
                System.out.println("\nAudit report written to: " + outPath.resolve("audit-report" + ext));
            }
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Audit failed: " + e.getMessage());
            System.err.println("Hint: Ensure your file contains @contract annotated classes for meaningful analysis.");
            System.exit(2);
        }
    }

    private static void handleDocs(Program program, CliOptions opts) {
        try {
            var docGen = new dhrlang.production.ContractDocGenerator();
            docGen.setProjectTitle(opts.filePath != null ? opts.filePath : "DhrLang Contracts");

            var docs = docGen.generateDocs(program);
            if (docs.isEmpty()) {
                System.out.println("No @contract classes found for documentation.");
                System.out.println("Hint: Annotate your class with @contract to generate docs.");
                return;
            }

            String rendered = docGen.renderMarkdown(docs);
            System.out.println(rendered);

            // Optionally write to file
            if (opts.outputDir != null) {
                java.nio.file.Path outPath = java.nio.file.Paths.get(opts.outputDir);
                Files.createDirectories(outPath);
                Files.writeString(outPath.resolve("contract-docs.md"), rendered);
                System.out.println("\nDocumentation written to: " + outPath.resolve("contract-docs.md"));
            }
        } catch (Exception e) {
            System.err.println("Documentation generation failed: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void handleDeployScript(Program program, CliOptions opts) {
        try {
            // First compile to EVM
            var compiler = new dhrlang.evm.EvmContractCompiler(program, errorReporter);
            var artifacts = compiler.compileAll();

            if (artifacts.isEmpty()) {
                System.out.println("No @contract classes found to deploy.");
                System.out.println("Hint: Annotate your class with @contract, then use --deploy-script.");
                return;
            }

            var deployer = new dhrlang.deploy.DeploymentManager();
            String script;
            if ("ethers".equals(opts.deployFormat)) {
                script = deployer.generateEthersScript(artifacts);
            } else {
                script = deployer.generateFoundryScript(artifacts);
            }

            System.out.println(script);

            // Optionally write to file
            if (opts.outputDir != null) {
                java.nio.file.Path outPath = java.nio.file.Paths.get(opts.outputDir);
                Files.createDirectories(outPath);
                String ext = "ethers".equals(opts.deployFormat) ? ".deploy.js" : ".deploy.sol";
                Files.writeString(outPath.resolve("Deploy" + ext), script);
                System.out.println("\nDeploy script written to: " + outPath.resolve("Deploy" + ext));
            }
        } catch (Exception e) {
            System.err.println("Deploy script generation failed: " + e.getMessage());
            System.err.println("Hint: Ensure @contract classes compile successfully first (try --compile-evm).");
            System.exit(2);
        }
    }

    private static void handleDebugEvm(Program program, CliOptions opts) {
        try {
            // First compile to EVM
            var compiler = new dhrlang.evm.EvmContractCompiler(program, errorReporter);
            var artifacts = compiler.compileAll();

            if (artifacts.isEmpty()) {
                System.out.println("No @contract classes found to debug.");
                System.out.println("Hint: Annotate your class with @contract to use the debugger.");
                return;
            }

            // Debug the first contract (or user could specify which)
            var artifact = artifacts.get(0);
            byte[] bytecode = artifact.getCreationBytecode();
            if (bytecode == null || bytecode.length == 0) {
                System.err.println("No bytecode generated for " + artifact.getContractName());
                return;
            }

            System.out.println("DhrLang EVM Debugger — " + artifact.getContractName());
            System.out.println("Bytecode size: " + bytecode.length + " bytes");
            System.out.println("Commands: step (s), continue (c), disasm (d), stack, quit (q)");
            System.out.println("═══════════════════════════════════════════════════");

            var debugger = new dhrlang.debug.ContractDebugger(bytecode);
            java.util.Scanner scanner = new java.util.Scanner(System.in);

            while (true) {
                System.out.print("dbg> ");
                if (!scanner.hasNextLine()) break;
                String cmd = scanner.nextLine().trim().toLowerCase();

                if (cmd.isEmpty()) continue;

                switch (cmd) {
                    case "s":
                    case "step": {
                        var state = debugger.step();
                        System.out.println("  PC=" + state.getPc() + " OP=" + state.getOpcode().name()
                                + " Gas=" + state.getGasUsed()
                                + (state.isHalted() ? " [HALTED]" : ""));
                        if (state.isHalted()) {
                            System.out.println("Execution complete.");
                            return;
                        }
                        break;
                    }
                    case "c":
                    case "continue": {
                        var state = debugger.continueExecution();
                        System.out.println("  Stopped at PC=" + state.getPc()
                                + (state.isHalted() ? " [HALTED]" : " [BREAKPOINT]"));
                        if (state.isHalted()) {
                            System.out.println("Execution complete after " + debugger.getStepNumber() + " steps.");
                            return;
                        }
                        break;
                    }
                    case "d":
                    case "disasm": {
                        String dis = debugger.getDisassembly(0, Math.min(bytecode.length, 64));
                        System.out.println(dis);
                        break;
                    }
                    case "stack": {
                        var state = debugger.step();
                        System.out.println("  Stack: " + state.getStack());
                        break;
                    }
                    case "r":
                    case "reset": {
                        debugger.reset();
                        System.out.println("  Debugger reset.");
                        break;
                    }
                    case "q":
                    case "quit":
                    case "exit":
                        System.out.println("Debugger exited.");
                        return;
                    default:
                        System.out.println("  Unknown command: " + cmd);
                        System.out.println("  Commands: step(s), continue(c), disasm(d), stack, reset(r), quit(q)");
                }
            }
        } catch (Exception e) {
            System.err.println("Debug session failed: " + e.getMessage());
            System.err.println("Hint: Ensure @contract classes compile to valid EVM bytecode first.");
            System.exit(2);
        }
    }

    private static class PhaseTimings {
        long lexMs, parseMs, typeMs, execMs, totalMs;
    }

    private static String serializeIr(dhrlang.ir.IrProgram p){
        StringBuilder sb = new StringBuilder();
        sb.append('{').append("\"irSchemaVersion\":1,\"functions\":[");
        for(int i=0;i<p.functions.size();i++){
            var f = p.functions.get(i);
            if(i>0) sb.append(',');
            sb.append('{').append("\"name\":\"").append(f.name).append("\",\"instructions\":[");
            for(int j=0;j<f.instructions.size();j++){
                if(j>0) sb.append(',');
                sb.append('"').append(f.instructions.get(j).toString().replace("\"","\\\"")).append('"');
            }
            sb.append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }
    
    private static void printRuntimeError(dhrlang.interpreter.DhrRuntimeException e, String sourceCode) {
        System.err.println();
        System.err.println("\u001B[91m╔══════════════════════════════════════════════════════════════╗\u001B[0m");
        System.err.println("\u001B[91m║                     RUNTIME ERROR                            ║\u001B[0m");
        System.err.println("\u001B[91m╚══════════════════════════════════════════════════════════════╝\u001B[0m");
        System.err.println();
        
        if (e.getLocation() != null) {
            System.err.println("\u001B[91m❌ Runtime Error:\u001B[0m \u001B[36m" + e.getLocation().toString() + "\u001B[0m - " + 
                             e.getCategory().getDisplayName() + ": " + (e.getValue() != null ? e.getValue().toString() : "null"));
            
            String[] lines = sourceCode.split("\n");
            int lineNum = e.getLocation().getLine();
            if (lineNum >= 1 && lineNum <= lines.length) {
                System.err.println();
                
                int startLine = Math.max(1, lineNum - 2);
                int endLine = Math.min(lines.length, lineNum + 2);
                
                int maxLineNumWidth = String.valueOf(endLine).length();
                
                for (int i = startLine; i <= endLine; i++) {
                    String line = lines[i - 1];
                    String lineNumStr = String.format("%" + maxLineNumWidth + "d", i);
                    
                    if (i == lineNum) {
                        System.err.println("\u001B[91m→ " + lineNumStr + " │ \u001B[0m\u001B[1m" + line + "\u001B[0m");
                        
                        if (e.getLocation().getColumn() > 0) {
                            System.err.print("\u001B[91m");
                            for (int j = 0; j < maxLineNumWidth + 3; j++) System.err.print(" ");
                            System.err.print("│ ");
                            for (int j = 0; j < e.getLocation().getColumn() - 1; j++) {
                                System.err.print(" ");
                            }
                            System.err.println("^\u001B[0m");
                        }
                    } else {
                        System.err.println("  " + lineNumStr + " │ " + line);
                    }
                }
                
                String hint = getErrorHint(e);
                if (hint != null) {
                    System.err.println();
                    System.err.println("\u001B[93m💡 Hint: " + hint + "\u001B[0m");
                }
            }
            System.err.println();
            System.err.println("\u001B[91m══════════════════════════════════════════════════════════════\u001B[0m");
        } else {
            // Fallback for errors without location
            System.err.println("\u001B[91m❌ Runtime Error:\u001B[0m " + e.getCategory().getDisplayName());
            System.err.println("\u001B[91mMessage:\u001B[0m " + (e.getValue() != null ? e.getValue().toString() : "null"));
            System.err.println();
            System.err.println("\u001B[91m══════════════════════════════════════════════════════════════\u001B[0m");
        }
    }
    
    private static String getErrorHint(dhrlang.interpreter.DhrRuntimeException e) {
        String message = e.getValue() != null ? e.getValue().toString() : "";
        
        switch (e.getCategory()) {
            case INDEX_ERROR:
                return ErrorMessages.getArrayIndexErrorHint();
            case TYPE_ERROR:
                if (message.contains("Generic types")) {
                    return "Generic types are used in declarations, not as runtime values. Use 'new ClassName<Type>()' to create instances.";
                }
                return "Check the types of your variables and operations";
            case NULL_ERROR:
                return "Make sure the object is properly initialized before use";
            case ARITHMETIC_ERROR:
                return ErrorMessages.getDivisionByZeroHint();
            case ACCESS_ERROR:
                if (message.contains("generic type") || message.contains("<") && message.contains(">")) {
                    return "Generic types cannot be accessed as variables. Use them in 'new' expressions or type declarations.";
                } else if (message.contains("Undefined variable")) {
                    return "Check variable spelling and scope. Use 'this.variableName' for instance variables.";
                }
                return "Check if you have proper access permissions to this member";
            case VALIDATION_ERROR:
                return ErrorMessages.getArrayValidationErrorHint(message);
            default:
                return "Check your code for potential issues";
        }
    }
    
    private static void printSystemError(RuntimeError e) {
        System.err.println();
        System.err.println("\u001B[91m╔══════════════════════════════════════════════════════════════╗\u001B[0m");
        System.err.println("\u001B[91m║                     SYSTEM ERROR                             ║\u001B[0m");
        System.err.println("\u001B[91m╚══════════════════════════════════════════════════════════════╝\u001B[0m");
        System.err.println();
        System.err.println("\u001B[91mError:\u001B[0m " + e.getMessage());
        System.err.println();
    }
}