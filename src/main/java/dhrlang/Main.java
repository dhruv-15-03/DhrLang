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
        boolean jsonMode = false;
        String filePath = null;
        for(String a: args){
            if("--json".equals(a)) jsonMode = true; else filePath = a; }
        if(filePath==null) filePath = "input/sample.dhr";

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
            if(jsonMode){
                System.out.println(errorReporter.toJson());
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
            
            System.exit(65);
        }

        if (errorReporter.hasWarnings()) {
            if(jsonMode){
                System.out.println(errorReporter.toJson());
                return; }
            System.err.println();
            System.err.println("\u001B[93m╔══════════════════════════════════════════════════════════════╗\u001B[0m");
            System.err.println("\u001B[93m║                        WARNINGS                             ║\u001B[0m");
            System.err.println("\u001B[93m╚══════════════════════════════════════════════════════════════╝\u001B[0m");
            System.err.println();
            errorReporter.printAllWarnings();
        }
    }

    private static void run(String sourceCode) {
        
        Lexer lexer = new Lexer(sourceCode, errorReporter);
        List<Token> tokens = lexer.scanTokens();

        if (errorReporter.hasErrors()) return;

        Parser parser = new Parser(tokens, errorReporter);
        Program program = null;

        try {
            program = parser.parse();
        } catch (ParseException e) {
        }

        if (errorReporter.hasErrors()) return;

        TypeChecker typeChecker = new TypeChecker(errorReporter);
        typeChecker.check(program);
        
        if (errorReporter.hasErrors()) return;
        
        try {
            Interpreter interpreter = new Interpreter();
            interpreter.execute(program);
        } catch (dhrlang.interpreter.DhrRuntimeException e) {
            printRuntimeError(e, sourceCode);
            System.exit(70);
        } catch (RuntimeError e) {
            printSystemError(e);
            System.exit(70);
        }
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