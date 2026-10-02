package dhrlang.runtime;

import dhrlang.bytecode.BytecodeVM;
import dhrlang.bytecode.BytecodeWriter;
import dhrlang.error.ErrorReporter;
import dhrlang.error.RuntimeErrorCategory;
import dhrlang.interpreter.DhrRuntimeException;
import dhrlang.ir.AstToIrLowerer;
import dhrlang.ir.IrInterpreter;
import dhrlang.ir.IrProgram;
import dhrlang.ir.opt.IrOptimizer;
import dhrlang.lexer.Lexer;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BackendExceptionRegressionTest {
    private IrProgram compile(String source) {
        ErrorReporter errors = new ErrorReporter();
        var program = new Parser(new Lexer(source, errors).scanTokens(), errors).parse();
        new TypeChecker(errors).check(program);
        assertFalse(errors.hasErrors(), errors.toJson());
        var ir = new AstToIrLowerer(errors).lower(program);
        assertFalse(errors.hasErrors(), errors.toJson());
        IrOptimizer.defaultPipeline().optimize(ir);
        return ir;
    }

    private void execute(IrProgram ir, boolean bytecode) {
        if (bytecode) new BytecodeVM().execute(new BytecodeWriter().write(ir));
        else new IrInterpreter().execute(ir);
    }

    @TestFactory
    Stream<DynamicTest> failuresEscapeTheEntryPointWithTheirCategory() {
        record Case(String body, RuntimeErrorCategory category) {}
        var cases = new Case[]{
                new Case("num[] a = [1]; printLine(a[2]);", RuntimeErrorCategory.INDEX_ERROR),
                new Case("printLine(1 / 0);", RuntimeErrorCategory.ARITHMETIC_ERROR),
                new Case("1 / 0;", RuntimeErrorCategory.ARITHMETIC_ERROR),
                new Case("1 % 0;", RuntimeErrorCategory.ARITHMETIC_ERROR),
                new Case("throw \"boom\";", RuntimeErrorCategory.USER_EXCEPTION),
                new Case("throw null;", RuntimeErrorCategory.USER_EXCEPTION)
        };
        return Stream.of(false, true).flatMap(bytecode -> Stream.of(cases).map(test ->
                DynamicTest.dynamicTest((bytecode ? "bytecode " : "ir ") + test.body, () -> {
                    var ir = compile("class Main { static kaam main() { fail(); }"
                            + " static kaam fail() { " + test.body + " } }");
                    var failure = assertThrows(DhrRuntimeException.class, () -> execute(ir, bytecode));
                    assertEquals(test.category, failure.getCategory());
                })));
    }

    @TestFactory
    Stream<DynamicTest> handledErrorsStillReachTypedCatchBlocks() {
        return Stream.of(false, true).map(bytecode -> DynamicTest.dynamicTest(
                bytecode ? "bytecode typed catch" : "ir typed catch", () -> {
                    var ir = compile("""
                            class Main {
                                static kaam main() {
                                    try { printLine(1 / 0); }
                                    catch (ArithmeticException failure) { return; }
                                    throw "catch was not executed";
                                }
                            }
                            """);
                    assertDoesNotThrow(() -> execute(ir, bytecode));
                }));
    }
}
