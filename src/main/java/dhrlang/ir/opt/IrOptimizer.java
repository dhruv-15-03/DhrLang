package dhrlang.ir.opt;

import dhrlang.ir.IrFunction;
import dhrlang.ir.IrProgram;

import java.util.List;

/** Runs a sequence of optimization passes over every function in an IrProgram. */
public class IrOptimizer {
    private final List<IrPass> passes;

    public IrOptimizer(List<IrPass> passes) {
        this.passes = passes;
    }

    /** Creates an optimizer with the default pass pipeline. */
    public static IrOptimizer defaultPipeline() {
        return new IrOptimizer(List.of(
                new ConstantFoldingPass(),
                new CopyPropagationPass(),
                new DeadStoreEliminationPass()
        ));
    }

    /** Optimize all functions in the program in place. */
    public void optimize(IrProgram program) {
        for (IrFunction fn : program.functions) {
            for (IrPass pass : passes) {
                pass.run(fn);
            }
        }
    }
}
