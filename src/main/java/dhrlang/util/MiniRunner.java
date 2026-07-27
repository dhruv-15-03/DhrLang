package dhrlang.util;

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
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MiniRunner {
    private MiniRunner() {}

    public static class Result {
        public final String stdout, stderr; public final boolean hadCompileErrors, hadRuntimeError; public Result(String o,String e,boolean c,boolean r){stdout=o;stderr=e;hadCompileErrors=c;hadRuntimeError=r;}
    }

    public static Result run(String source){
        ErrorReporter er = new ErrorReporter();
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream oOut = System.out, oErr = System.err;
        System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        boolean compileErr=false;
        try {
            Lexer lexer = new Lexer(source, er);
            List<Token> tokens = lexer.scanTokens();
            if(er.hasErrors()) return new Result(outBuf.toString(), errBuf.toString(), true,false);
            Parser parser = new Parser(tokens, er); Program program=null; try { program = parser.parse(); } catch(ParseException e){ compileErr=true; }
            if(er.hasErrors() || program==null) return new Result(outBuf.toString(), errBuf.toString(), true,false);
            TypeChecker tc = new TypeChecker(er); tc.check(program); if(er.hasErrors()) return new Result(outBuf.toString(), errBuf.toString(), true,false);
            try {
                new Interpreter().execute(program);
            } catch (dhrlang.interpreter.DhrRuntimeException e){
                if(errBuf.size()==0){
                    String msg = String.valueOf(e.getMessage());
                    errBuf.write(msg.getBytes(StandardCharsets.UTF_8));
                }
                return new Result(outBuf.toString(StandardCharsets.UTF_8), errBuf.toString(StandardCharsets.UTF_8), compileErr, true);
            }
            return new Result(outBuf.toString(), errBuf.toString(), compileErr,false);
        } catch(Exception ex){
            String exMsg = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
            return new Result(outBuf.toString(StandardCharsets.UTF_8), errBuf.toString(StandardCharsets.UTF_8)+"\nEX:"+exMsg, true,true);
        }
        finally { System.setOut(oOut); System.setErr(oErr);} }
}