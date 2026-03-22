package dhrlang.ir.opt;

import dhrlang.ir.IrFunction;

/** A single optimization pass over an IR function's instruction list. */
public interface IrPass {
    /** The human-readable name of this pass (for diagnostics). */
    String name();

    /** Apply this pass to the given function, mutating its instruction list in place. */
    void run(IrFunction fn);
}
