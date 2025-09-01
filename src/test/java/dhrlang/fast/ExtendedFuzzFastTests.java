package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class ExtendedFuzzFastTests {
  private static final String[] VARS = {"a","b","c"};
  private static final String[] OPS = {"+","-","*","/"};
  private String randExpr(Random r, int depth){
    if(depth<=0) return String.valueOf(r.nextInt(5)+1);
    int kind = r.nextInt(4);
    if(kind==0){ return VARS[r.nextInt(VARS.length)]; }
    if(kind==1){ return "("+randExpr(r, depth-1)+OPS[r.nextInt(OPS.length)]+randExpr(r, depth-1)+")"; }
    if(kind==2){ return randExpr(r, depth-1)+OPS[r.nextInt(OPS.length)]+randExpr(r, depth-1); }
    return String.valueOf(r.nextInt(10));
  }
  private String buildProgram(Random r){
    StringBuilder sb = new StringBuilder();
    sb.append("class A { static kaam main(){ ");
    sb.append("num a=1; num b=2; num c=3; ");
    int stmts = 5 + r.nextInt(5);
    for(int i=0;i<stmts;i++){
      switch(r.nextInt(5)){
        case 0 -> sb.append("a = ").append(randExpr(r,3)).append(";\n");
        case 1 -> sb.append("b = ").append(randExpr(r,3)).append(";\n");
        case 2 -> sb.append("c = ").append(randExpr(r,3)).append(";\n");
        case 3 -> sb.append("if(").append(randExpr(r,2)).append(") { a = a + 1; }\n");
        default -> sb.append("print(").append(randExpr(r,2)).append(");\n");
      }
    }
    sb.append(" } }");
    return sb.toString();
  }
  @Test void extendedFuzzNoHardCrash(){
    Random r = new Random(1234);
    for(int i=0;i<200;i++){
      String prog = buildProgram(r);
      var res = RuntimeTestUtil.runSource(prog);
      // Allow semantic runtime errors (division by zero, etc.) but disallow internal crashes indicated by empty stderr with runtimeError flag false (already handled) or unexpected exception text patterns.
      if(res.hadRuntimeError){
        // Accept known arithmetic or index errors; if stderr lacks a recognized category phrase, flag.
        String err = res.stderr.toLowerCase();
        boolean known = err.contains("division") || err.contains("index") || err.contains("access") || err.contains("type") || err.contains("null");
        assertTrue(known, "Unexpected runtime error category. Program:\n"+prog+"\nSTDERR:\n"+res.stderr);
      }
    }
  }
}
