package dhrlang.repl;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.eval.Evaluator;
import dhrlang.interpreter.Environment;
import dhrlang.interpreter.Interpreter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive REPL (Read-Eval-Print Loop) for DhrLang.
 * Allows users to type expressions and statements interactively.
 *
 * Usage: java -jar DhrLang.jar --repl
 */
public final class DhrLangRepl {

    private static final String PROMPT = "dhr> ";
    private static final String CONTINUED = "...> ";

    private DhrLangRepl() {}

    public static void startRepl() {
        PrintStream out = System.out;
        out.println("DhrLang REPL v4.0.0");
        out.println("Type expressions or statements. Use 'exit' or Ctrl+D to quit.");
        out.println("Wrap multi-line code in a class: class Main { static void main() { ... } }");
        out.println();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Interpreter interpreter = new Interpreter();
        StringBuilder buffer = new StringBuilder();
        boolean multiLine = false;

        while (true) {
            try {
                out.print(multiLine ? CONTINUED : PROMPT);
                out.flush();
                String line = reader.readLine();
                if (line == null) { // EOF (Ctrl+D)
                    out.println("\nGoodbye!");
                    break;
                }
                line = line.trim();
                if ("exit".equals(line) || "quit".equals(line)) {
                    out.println("Goodbye!");
                    break;
                }
                if (line.isEmpty()) continue;

                // Accumulate lines for multi-line input
                buffer.append(line).append('\n');
                String input = buffer.toString().trim();

                // Check if braces are balanced
                int braceCount = 0;
                for (char c : input.toCharArray()) {
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                }
                if (braceCount > 0) {
                    multiLine = true;
                    continue;
                }
                multiLine = false;
                buffer.setLength(0);

                // Try to evaluate
                evaluateInput(input, interpreter, out);

            } catch (Exception e) {
                out.println("Error: " + e.getMessage());
                buffer.setLength(0);
                multiLine = false;
            }
        }
    }

    private static void evaluateInput(String input, Interpreter interpreter, PrintStream out) {
        ErrorReporter reporter = new ErrorReporter("repl", input);
        reporter.setColorEnabled(true);

        // If input looks like a class declaration, parse as-is
        // Otherwise, wrap in a Main class for quick evaluation
        String source;
        if (input.contains("class ") || input.contains("interface ") || input.contains("enum ")) {
            source = input;
        } else {
            // Wrap expression/statement in a class
            source = "class Main {\n  static void main() {\n    " + input + "\n  }\n}";
        }

        try {
            Lexer lexer = new Lexer(source, reporter);
            List<Token> tokens = lexer.scanTokens();
            if (reporter.hasErrors()) {
                reporter.printAllErrors();
                return;
            }

            Parser parser = new Parser(tokens, reporter);
            Program program = parser.parse();
            if (reporter.hasErrors()) {
                reporter.printAllErrors();
                return;
            }

            // Skip type checking in REPL for faster feedback
            interpreter.execute(program);

        } catch (dhrlang.parser.ParseException e) {
            if (!reporter.hasErrors()) {
                out.println("Parse error: " + e.getMessage());
            } else {
                reporter.printAllErrors();
            }
        } catch (dhrlang.interpreter.DhrRuntimeException e) {
            out.println("Runtime error: " + e.getMessage());
        } catch (Exception e) {
            out.println("Error: " + e.getMessage());
        }
    }
}
