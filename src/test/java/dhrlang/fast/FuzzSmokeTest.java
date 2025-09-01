package dhrlang.fast;

import dhrlang.runtime.RuntimeTestUtil;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class FuzzSmokeTest {
    private static final String[] ATOMS = {"1","2","3","4","5","6","7","8","9"};
    private static final String[] BIN = {"+","-","*","/"};
    private String gen(Random rnd, int depth){
        if(depth==0) return ATOMS[rnd.nextInt(ATOMS.length)];
        if(rnd.nextDouble()<0.3) return ATOMS[rnd.nextInt(ATOMS.length)];
        String left = gen(rnd, depth-1);
        String right = gen(rnd, depth-1);
        String op = BIN[rnd.nextInt(BIN.length)];
        return "("+left+op+right+")";
    }
    @Test void fuzzArithmeticNoCrash(){
        Random rnd = new Random(12345);
        for(int i=0;i<200;i++){
            String expr = gen(rnd, 4);
            String src = "class F { static kaam main(){ duo x = "+expr+"; } }";
            var r = RuntimeTestUtil.runSource(src);
            assertFalse(r.hadCompileErrors, "Compile error on expr "+expr+": "+r.stderr);
            // Allow runtime division by zero to surface as runtime error; don't fail test for that.
        }
    }
}
