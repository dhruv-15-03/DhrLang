package dhrlang.runtime;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.interpreter.Interpreter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.ParseException;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RuntimeTestUtil {
    private RuntimeTestUtil() {}

    public static class Result {
        public final String stdout;
        public final String stderr;
        public final boolean hadCompileErrors;
        public final boolean hadRuntimeError;
        // Optional details for runtime errors
        public final String runtimeErrorMessage;
        public final String runtimeErrorCategory;

        public Result(String out, String err, boolean ce, boolean re) {
            this(out, err, ce, re, null, null);
        }

        public Result(String out, String err, boolean ce, boolean re, String remsg, String recat) {
            this.stdout = out;
            this.stderr = err;
            this.hadCompileErrors = ce;
            this.hadRuntimeError = re;
            this.runtimeErrorMessage = remsg;
            this.runtimeErrorCategory = recat;
        }
    }

    public static Result runFile(String path) throws Exception {
        String source = Files.readString(Path.of(path));
        return runSource(source);
    }

    public static Result runSource(String source) {
    ErrorReporter errorReporter = new ErrorReporter();
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    PrintStream origOut = System.out; PrintStream origErr = System.err;
    System.setOut(new PrintStream(outBuf)); System.setErr(new PrintStream(errBuf));
    boolean compileError = false;
        try {
            Lexer lexer = new Lexer(source, errorReporter);
            List<Token> tokens = lexer.scanTokens();
            if (errorReporter.hasErrors()) { compileError = true; return new Result(outBuf.toString().trim(), errBuf.toString(), true, false); }
            Parser parser = new Parser(tokens, errorReporter);
            Program program = null;
            try { program = parser.parse(); } catch (ParseException e) { compileError = true; }
            if (errorReporter.hasErrors() || program == null) { compileError = true; return new Result(outBuf.toString().trim(), errBuf.toString(), true, false); }
            TypeChecker tc = new TypeChecker(errorReporter);
            tc.check(program);
            if (errorReporter.hasErrors()) { 
                compileError = true; 
                var errs = errorReporter.getErrors();
                if(errs!=null && !errs.isEmpty()) {
                    PrintStream ps = new PrintStream(errBuf);
                    errs.forEach(e-> ps.println((e.getCode()!=null? ("["+e.getCode()+"] "):"")+ e.getMessage()));
                }
                return new Result(outBuf.toString().trim(), errBuf.toString(), true, false); 
            }
            try {
                Interpreter interpreter = new Interpreter();
                interpreter.execute(program);
            } catch (dhrlang.interpreter.DhrRuntimeException e) {
                // Mirror CLI behavior: surface runtime error message on stderr for tests that scan stderr
                new PrintStream(errBuf).println(e.getMessage());
                return new Result(outBuf.toString().trim(), errBuf.toString(), compileError, true, e.getMessage(), e.getCategory().getDisplayName());
            }
            return new Result(outBuf.toString().trim(), errBuf.toString(), compileError, false);
        } catch (Exception ex) {
            return new Result(outBuf.toString().trim(), errBuf.toString() + "\nEXCEPTION:" + ex.getMessage(), true, true);
        } finally {
            System.setOut(origOut); System.setErr(origErr);
        }
    }
}
