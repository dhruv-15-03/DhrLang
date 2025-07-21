package dhrlang;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.RuntimeError;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.parser.ParseException;
import dhrlang.typechecker.TypeChecker;
import dhrlang.typechecker.TypeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static ErrorReporter errorReporter = new ErrorReporter();

    public static void main(String[] args) {
        String filePath = args.length > 0 ? args[0] : "input/sample.dhr";

        try {
            String sourceCode = Files.readString(Path.of(filePath));
            errorReporter.setSource(filePath, sourceCode);
            run(sourceCode);
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath);
            e.printStackTrace();
            System.exit(1);
        }

        if (errorReporter.hasErrors()) {
            System.out.println("\n=== Compilation Failed ===");
            System.out.println("Found " + errorReporter.getErrorCount() + " error(s):\n");
            errorReporter.printAllErrors();
            System.exit(65);
        }

        if (errorReporter.hasWarnings()) {
            System.out.println("\n=== Warnings ===");
            errorReporter.printAllWarnings();
        }
    }

    private static void run(String sourceCode) {
        System.out.println("Starting compilation...");

        Lexer lexer = new Lexer(sourceCode, errorReporter);
        List<Token> tokens = lexer.scanTokens();
        System.out.println("Lexing completed. Token count: " + tokens.size());

        if (errorReporter.hasErrors()) return;

        Parser parser = new Parser(tokens);
        Program program = null;

        try {
            System.out.println("Starting parsing...");
            program = parser.parse();
            System.out.println("Parsing completed successfully!");
        } catch (ParseException e) {
            System.out.println("Parse error occurred!");
            if (e.getToken() != null) {
                errorReporter.error(e.getToken().getLocation(), e.getMessage());
            } else {
                errorReporter.error(e.getLine(), e.getMessage());
            }
        }

        if (errorReporter.hasErrors()) return;

        TypeChecker typeChecker = new TypeChecker();
        try {
            typeChecker.check(program);
        } catch (TypeException e) {
            errorReporter.error(0, e.getMessage());
        }

        if (errorReporter.hasErrors()) return;
        
        try {
            Interpreter interpreter = new Interpreter();
            interpreter.execute(program);
        } catch (dhrlang.interpreter.DhrRuntimeException e) {
            System.err.println();
            System.err.println(e.getDetailedMessage());
            if (e.getLocation() != null) {
                errorReporter.error(e.getLocation(), "Uncaught exception: " + (e.getValue() != null ? e.getValue().toString() : "null"));
                errorReporter.printAllErrors();
            }
            System.exit(70);
        } catch (RuntimeError e) {
            System.err.println("Runtime Error: " + e.getMessage());
            System.exit(70);
        }
    }
}