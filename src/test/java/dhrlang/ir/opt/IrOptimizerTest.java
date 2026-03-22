package dhrlang.ir.opt;

import dhrlang.ir.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IR Optimizer Pass Tests")
public class IrOptimizerTest {

    private IrFunction fn(IrInstruction... insns) {
        IrFunction f = new IrFunction("test");
        f.instructions.addAll(List.of(insns));
        return f;
    }

    @Nested
    @DisplayName("Constant Folding")
    class ConstantFoldingTests {

        private final ConstantFoldingPass pass = new ConstantFoldingPass();

        @Test
        @DisplayName("Fold integer addition: 10 + 3 = 13")
        void foldIntegerAdd() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrConst(1, 3L),
                    new IrBinOp(IrBinOp.Op.ADD, 0, 1, 2),
                    new IrPrint(2, true)
            );
            pass.run(f);
            // BinOp should be replaced by IrConst(2, 13L)
            assertInstanceOf(IrConst.class, f.instructions.get(2));
            IrConst folded = (IrConst) f.instructions.get(2);
            assertEquals(13L, folded.value);
            assertEquals(2, folded.targetSlot);
        }

        @Test
        @DisplayName("Fold preserves integer type (Long, not Double)")
        void foldPreservesIntegerType() {
            IrFunction f = fn(
                    new IrConst(0, 5L),
                    new IrConst(1, 7L),
                    new IrBinOp(IrBinOp.Op.MUL, 0, 1, 2)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(2);
            assertInstanceOf(Long.class, folded.value);
            assertEquals(35L, folded.value);
        }

        @Test
        @DisplayName("Fold double addition: 1.5 + 2.5 = 4.0")
        void foldDoubleAdd() {
            IrFunction f = fn(
                    new IrConst(0, 1.5),
                    new IrConst(1, 2.5),
                    new IrBinOp(IrBinOp.Op.ADD, 0, 1, 2)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(2);
            assertInstanceOf(Double.class, folded.value);
            assertEquals(4.0, folded.value);
        }

        @Test
        @DisplayName("Fold subtraction and modulo")
        void foldSubAndMod() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrConst(1, 3L),
                    new IrBinOp(IrBinOp.Op.SUB, 0, 1, 2),
                    new IrBinOp(IrBinOp.Op.MOD, 0, 1, 3)
            );
            pass.run(f);
            assertEquals(7L, ((IrConst) f.instructions.get(2)).value);
            assertEquals(1L, ((IrConst) f.instructions.get(3)).value);
        }

        @Test
        @DisplayName("Don't fold division by zero")
        void noFoldDivByZero() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrConst(1, 0L),
                    new IrBinOp(IrBinOp.Op.DIV, 0, 1, 2)
            );
            pass.run(f);
            // Should NOT be folded — keep the BinOp so runtime error is raised
            assertInstanceOf(IrBinOp.class, f.instructions.get(2));
        }

        @Test
        @DisplayName("Fold unary negation")
        void foldNeg() {
            IrFunction f = fn(
                    new IrConst(0, 5L),
                    new IrUnaryOp(IrUnaryOp.Op.NEG, 0, 1)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(1);
            assertEquals(-5L, folded.value);
        }

        @Test
        @DisplayName("Fold boolean NOT")
        void foldNot() {
            IrFunction f = fn(
                    new IrConst(0, true),
                    new IrUnaryOp(IrUnaryOp.Op.NOT, 0, 1)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(1);
            assertEquals(false, folded.value);
        }

        @Test
        @DisplayName("Fold comparison: 10 > 3 = true")
        void foldCompare() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrConst(1, 3L),
                    new IrCompare(IrCompare.Op.GT, 0, 1, 2)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(2);
            assertEquals(true, folded.value);
        }

        @Test
        @DisplayName("Fold string concatenation")
        void foldStringConcat() {
            IrFunction f = fn(
                    new IrConst(0, "hello"),
                    new IrConst(1, " world"),
                    new IrBinOp(IrBinOp.Op.ADD, 0, 1, 2)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(2);
            assertEquals("hello world", folded.value);
        }

        @Test
        @DisplayName("Constants invalidated at labels")
        void labelsInvalidateConstants() {
            IrFunction f = fn(
                    new IrConst(0, 5L),
                    new IrLabel("merge"),
                    new IrConst(1, 3L),
                    new IrBinOp(IrBinOp.Op.ADD, 0, 1, 2)
            );
            pass.run(f);
            // s0 is invalidated at label, so BinOp should NOT be folded
            assertInstanceOf(IrBinOp.class, f.instructions.get(3));
        }

        @Test
        @DisplayName("Constants propagate through StoreLocal/LoadLocal")
        void constantsPropagateThoughCopies() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrStoreLocal(0, 1),    // local 1 = 10L
                    new IrConst(2, 3L),
                    new IrLoadLocal(1, 3),      // s3 = local 1 = 10L
                    new IrBinOp(IrBinOp.Op.ADD, 3, 2, 4)
            );
            pass.run(f);
            IrConst folded = (IrConst) f.instructions.get(4);
            assertEquals(13L, folded.value);
        }
    }

    @Nested
    @DisplayName("Copy Propagation")
    class CopyPropagationTests {

        private final CopyPropagationPass pass = new CopyPropagationPass();

        @Test
        @DisplayName("Propagate copy in BinOp operands")
        void propagateCopyInBinOp() {
            // s1 = s0; s3 = s1 + s2 → s3 = s0 + s2
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrLoadLocal(0, 1),
                    new IrConst(2, 3L),
                    new IrBinOp(IrBinOp.Op.ADD, 1, 2, 3)
            );
            pass.run(f);
            IrBinOp binOp = (IrBinOp) f.instructions.get(3);
            assertEquals(0, binOp.leftSlot); // propagated from s1 → s0
        }

        @Test
        @DisplayName("Propagate copy in Print")
        void propagateCopyInPrint() {
            IrFunction f = fn(
                    new IrConst(0, 42L),
                    new IrLoadLocal(0, 1),
                    new IrPrint(1, true)
            );
            pass.run(f);
            IrPrint print = (IrPrint) f.instructions.get(2);
            assertEquals(0, print.slot);
        }

        @Test
        @DisplayName("Copy chain resolution")
        void copyChainResolution() {
            // s1 = s0; s2 = s1; print s2 → print s0
            IrFunction f = fn(
                    new IrConst(0, 42L),
                    new IrLoadLocal(0, 1),
                    new IrLoadLocal(1, 2),
                    new IrPrint(2, true)
            );
            pass.run(f);
            IrPrint print = (IrPrint) f.instructions.get(3);
            assertEquals(0, print.slot);
        }

        @Test
        @DisplayName("Copies invalidated at labels")
        void copiesInvalidatedAtLabels() {
            IrFunction f = fn(
                    new IrConst(0, 42L),
                    new IrLoadLocal(0, 1),
                    new IrLabel("merge"),
                    new IrPrint(1, true)   // should NOT be propagated past label
            );
            pass.run(f);
            IrPrint print = (IrPrint) f.instructions.get(3);
            assertEquals(1, print.slot); // unchanged
        }
    }

    @Nested
    @DisplayName("Dead Store Elimination")
    class DeadStoreEliminationTests {

        private final DeadStoreEliminationPass pass = new DeadStoreEliminationPass();

        @Test
        @DisplayName("Remove unused IrConst")
        void removeUnusedConst() {
            IrFunction f = fn(
                    new IrConst(0, 42L),      // unused
                    new IrConst(1, 10L),
                    new IrPrint(1, true),
                    new IrReturn(null)
            );
            pass.run(f);
            assertEquals(3, f.instructions.size()); // IrConst(0) removed
            assertInstanceOf(IrConst.class, f.instructions.get(0));
            assertEquals(10L, ((IrConst) f.instructions.get(0)).value);
        }

        @Test
        @DisplayName("Remove unused BinOp result")
        void removeUnusedBinOp() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrConst(1, 3L),
                    new IrBinOp(IrBinOp.Op.ADD, 0, 1, 2),  // result in s2 never used
                    new IrPrint(0, true),
                    new IrReturn(null)
            );
            pass.run(f);
            // BinOp should be removed since s2 is never referenced
            assertTrue(f.instructions.stream().noneMatch(i -> i instanceof IrBinOp));
        }

        @Test
        @DisplayName("Keep side-effecting instructions")
        void keepSideEffects() {
            IrFunction f = fn(
                    new IrConst(0, 42L),
                    new IrPrint(0, true),      // side effect: print
                    new IrSetStatic("X", "f", 0),  // side effect: write static
                    new IrReturn(null)
            );
            pass.run(f);
            assertEquals(4, f.instructions.size()); // nothing removed
        }
    }

    @Nested
    @DisplayName("Full Optimizer Pipeline")
    class FullPipelineTests {

        @Test
        @DisplayName("Pipeline folds constants and eliminates dead stores")
        void fullPipeline() {
            IrFunction f = fn(
                    new IrConst(0, 10L),
                    new IrConst(1, 3L),
                    new IrBinOp(IrBinOp.Op.ADD, 0, 1, 2),
                    new IrConst(3, 99L),        // unused
                    new IrPrint(2, true),
                    new IrReturn(null)
            );
            IrOptimizer optimizer = IrOptimizer.defaultPipeline();
            IrProgram program = new IrProgram();
            program.functions.add(f);
            optimizer.optimize(program);

            // After folding, BinOp replaced by IrConst(2, 13L)
            // After DSE, IrConst(0, 10L) and IrConst(1, 3L) removed (read by folded-away BinOp)
            // IrConst(3, 99L) also removed (unused)
            // Result: IrConst(2, 13L), IrPrint(2), IrReturn
            boolean hasFoldedConst = f.instructions.stream()
                    .anyMatch(i -> i instanceof IrConst c && c.targetSlot == 2 && Long.valueOf(13L).equals(c.value));
            assertTrue(hasFoldedConst, "Should have folded constant 13L in slot 2");
        }
    }
}
