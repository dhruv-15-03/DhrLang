package dhrlang.evm;

import dhrlang.ast.Program;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.parser.Parser;
import dhrlang.typechecker.TypeChecker;
import dhrlang.validation.StorageLayouter;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EvmCorrectnessRegressionTest {
    private Program parse(String body) {
        String source = "@contract class Probe { @constructor kaam init() {} " + body + " }";
        ErrorReporter errors = new ErrorReporter();
        Program program = new Parser(new Lexer(source, errors).scanTokens(), errors).parse();
        new TypeChecker(errors).check(program);
        assertFalse(errors.hasErrors(), errors.toJson());
        return program;
    }

    private byte[] compile(String body) {
        return new EvmContractCompiler(parse(body)).compileAll().get(0).getRuntimeBytecode();
    }

    @Test
    void largeLiteralsAreNotNarrowedToInt() {
        byte[] large = compile("@view num value() { return 4294967296; }");
        byte[] zero = compile("@view num value() { return 0; }");
        assertFalse(Arrays.equals(large, zero));
        byte[] next = compile("@view num value() { return 4294967297; }");
        assertFalse(Arrays.equals(next, compile("@view num value() { return 1; }")));
    }

    @Test
    void internalCallsAreRejectedWithSourceInformation() {
        var error = assertThrows(IllegalStateException.class, () -> compile("""
                @pure num helper() { return 7; }
                @view num value() { return helper(); }
                """));
        assertTrue(error.getMessage().contains("calls are not supported"));
        assertTrue(error.getMessage().contains("line"));
    }

    @Test
    void unsupportedFloatingPointDoesNotSilentlyTruncate() {
        var error = assertThrows(IllegalStateException.class,
                () -> compile("@pure duo value() { return 1.25; }"));
        assertTrue(error.getMessage().contains("Floating-point"));
    }

    @Test
    void compilerDoesNotRewriteAlreadyResolvedBytecode() {
        Program program = parse("@pure num add(num a, num b) { return a + b; }");
        var cls = program.getClasses().get(0);
        StorageLayouter layouter = new StorageLayouter();
        layouter.layoutAll(program);
        var raw = new EvmCodeGen(cls, layouter.getLayout(cls.getName())).compile();
        var compiled = new EvmContractCompiler(program).compileAll().get(0);
        assertArrayEquals(raw.getRuntimeBytecode(), compiled.getRuntimeBytecode());
        assertArrayEquals(raw.getCreationBytecode(), compiled.getCreationBytecode());
        byte[] creation = compiled.getCreationBytecode();
        byte[] runtime = compiled.getRuntimeBytecode();
        assertArrayEquals(runtime, Arrays.copyOfRange(creation, creation.length - runtime.length, creation.length));
    }

    @Test
    void negativeLongsAreSignExtendedToTheFullEvmWord() {
        EvmCodeBuffer buffer = new EvmCodeBuffer();
        buffer.pushInt(Long.MIN_VALUE);
        byte[] code = buffer.resolve();
        assertEquals(33, code.length);
        assertEquals(BigInteger.ONE.shiftLeft(256).add(BigInteger.valueOf(Long.MIN_VALUE)),
                new BigInteger(1, Arrays.copyOfRange(code, 1, code.length)));
    }

    @Test
    void peepholeFoldingDoesNotUseEightBitArithmeticOrReverseSubtraction() {
        byte[] add = {0x60, (byte) 255, 0x60, 1, 1};
        assertArrayEquals(add, EvmPeepholeOptimizer.optimize(add));
        byte[] multiply = {0x60, 100, 0x60, 3, 2};
        assertArrayEquals(multiply, EvmPeepholeOptimizer.optimize(multiply));
        assertArrayEquals(new byte[]{0x60, 5},
                EvmPeepholeOptimizer.optimize(new byte[]{0x60, 2, 0x60, 7, 3}));
    }

    @Test
    void optimizerLeavesPositionDependentCodeUntouched() {
        byte[] jump = {0x60, 0, 0x50, 0x60, 6, 0x56, 0x5b, 0};
        assertArrayEquals(jump, EvmPeepholeOptimizer.optimize(jump));
        byte[] copy = {0x60, 0, 0x60, 0, 0x60, 0, 0x39};
        assertArrayEquals(copy, EvmPeepholeOptimizer.optimize(copy));
    }
}
