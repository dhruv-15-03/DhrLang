package dhrlang;

import dhrlang.typechecker.TypeDesc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WildcardGenericsTest {
    @Test
    void wildcardAssignability() {
        TypeDesc listNum = TypeDesc.parse("List<num>");
        TypeDesc listAny = TypeDesc.parse("List<_>");
        assertTrue(TypeDesc.assignable(listNum, listAny));
        assertTrue(TypeDesc.assignable(listAny, listNum));
        TypeDesc mapSpecific = TypeDesc.parse("Map<num, sab>");
        TypeDesc mapWild = TypeDesc.parse("Map<_, _>");
        assertTrue(TypeDesc.assignable(mapSpecific, mapWild));
        assertTrue(TypeDesc.assignable(mapWild, mapSpecific));
    }
}