package dhrlang.fast;

import dhrlang.typechecker.TypeDesc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MultiDimArrayFastTests {
    @Test
    void parseTypeDescForMultiD() {
        assertEquals("num[]", TypeDesc.parse("num[]").toString());
        assertEquals("sab[][]", TypeDesc.parse("sab[][]").toString());
    }
}
