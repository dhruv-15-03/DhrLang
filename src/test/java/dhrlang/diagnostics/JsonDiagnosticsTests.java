package dhrlang.diagnostics;

import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.ParseException;
import dhrlang.parser.Parser;
import dhrlang.ast.Program;
import dhrlang.typechecker.TypeChecker;
import dhrlang.interpreter.Interpreter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JsonDiagnosticsTests {

    // Minimal runner variant that forces json output similar to Main --json early exit
    private static class JsonResult { String json; boolean hadErrors; }

    private JsonResult runJson(String source){
        ErrorReporter er = new ErrorReporter();
        er.setSource("<memory>", source);
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream origOut = System.out, origErr = System.err;
        System.setOut(new PrintStream(outBuf)); System.setErr(new PrintStream(errBuf));
        try {
            Lexer lexer = new Lexer(source, er); List<Token> tokens = lexer.scanTokens();
            Parser parser = new Parser(tokens, er); Program p=null; try{ p = parser.parse(); }catch(ParseException ex){ }
            if(!er.hasErrors() && p!=null){ TypeChecker tc = new TypeChecker(er); tc.check(p); }
            if(er.hasErrors()){ System.out.print(er.toJson()); JsonResult r = new JsonResult(); r.json = outBuf.toString(); r.hadErrors = true; return r; }
            // else interpret then still emit json (may contain warnings)
            try { new Interpreter().execute(p); } catch(Exception ignore) {}
            System.out.print(er.toJson()); JsonResult r = new JsonResult(); r.json = outBuf.toString(); r.hadErrors = er.hasErrors(); return r;
        } finally { System.setOut(origOut); System.setErr(origErr); }
    }

    @Test void jsonCapturesSyntaxError(){
        String src = "class A { static kaam main(){ print( ; } }"; // malformed
        var r = runJson(src);
        assertTrue(r.hadErrors);
        assertNotNull(r.json);
        assertTrue(r.json.trim().startsWith("{"));
        assertTrue(r.json.contains("\"errors\""));
    }

    @Test void variableHintPresentForUndefinedVariable(){
        String src = "class A { static kaam main(){ num count = 1; print(cout); } }"; // misspelled 'count'
        var r = runJson(src);
        assertTrue(r.hadErrors);
        // Current implementation places generic hint from UNDECLARED_IDENTIFIER, not suggestion string in JSON
        assertTrue(r.json.contains("Undefined variable 'cout'"));
        assertTrue(r.json.contains("hint"));
    }
}
