package dhrlang.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Placeholder IR program container (Phase 0).
 * Will be populated by lowering pipeline in later phases.
 */
public class IrProgram {
    public final List<IrFunction> functions = new ArrayList<>();
}
