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
        if(options.timeMode && !options.jsonMode){
            printTimings(timings);
        } else if(options.timeMode && options.jsonMode && !errorReporter.hasErrors()) {
            System.out.println(buildJsonOutput(options, timings));
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
    System.out.println("  --help           Show this help and exit");
    System.out.println("  --version        Print version and exit");
    System.out.println("  --json           Emit diagnostics as JSON (errors/warnings)");
    System.out.println("  --time           Show phase timings (lex/parse/type/exec)");
    System.out.println("  --no-color       Disable ANSI colors in diagnostics");
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
    }

    private static CliOptions parseArgs(String[] args) {
        CliOptions opts = new CliOptions();
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
        if(errorReporter.hasErrors()){ pt.totalMs = msSince(tStart); return pt; }

        s = System.nanoTime();
        try {
            if("ir".equalsIgnoreCase(opts.backend)) {
                System.out.println("[experimental] IR backend selected (lowering subset active)\n");
                dhrlang.ir.AstToIrLowerer lowerer = new dhrlang.ir.AstToIrLowerer(errorReporter);
                dhrlang.ir.IrProgram irProgram = lowerer.lower(program);
                if(opts.emitIr){
                    System.out.println(serializeIr(irProgram));
                }
                // Execute IR (subset) then fall back to AST for full semantics until parity
                new dhrlang.ir.IrInterpreter().execute(irProgram);
                Interpreter fallback = new Interpreter();
                fallback.execute(program);
            } else if("bytecode".equalsIgnoreCase(opts.backend)) {
                System.out.println("[experimental] Bytecode backend selected (alpha)\n");
                dhrlang.ir.AstToIrLowerer lowerer = new dhrlang.ir.AstToIrLowerer(errorReporter);
                dhrlang.ir.IrProgram irProgram = lowerer.lower(program);
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
                // Fallback to AST for correctness until full parity
                Interpreter fallback = new Interpreter();
                fallback.execute(program);
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