package dhrlang.bytecode;

import dhrlang.interpreter.DhrRuntimeException;
import dhrlang.ir.*;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class BytecodeVmEdgeTests {

    private static String runVm(IrProgram program) {
        byte[] bc = new BytecodeWriter().write(program);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream prev = System.out;
        System.setOut(new PrintStream(baos));
        try {
            new BytecodeVM().execute(bc);
        } finally {
            System.setOut(prev);
        }
        return baos.toString().replace("\r\n", "\n").trim();
    }

    @Test
    void rejectsBadMagic() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x0);
            out.writeInt(2);
            out.writeInt(0);
            out.writeInt(0);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("magic"));
    }

    @Test
    void rejectsBadVersion() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(999);
            out.writeInt(0);
            out.writeInt(0);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("version"));
    }

    @Test
    void rejectsUnknownConstTag() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(1);
            out.writeByte(99);
            out.writeInt(0);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("const"));
    }

    @Test
    void rejectsUnknownOpcode() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(0);
            out.writeInt(1);
            out.writeUTF("Main.main");
            out.writeInt(1);
            out.writeInt(999);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("opcode"));
    }

    @Test
    void rejectsInvalidJumpTarget() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(0); // cp
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(1); // 1 instruction
            out.writeInt(BytecodeOpcode.JUMP.code);
            out.writeInt(-1); // invalid target
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("jumptarget") || ex.getMessage().toLowerCase().contains("jump"));
    }

    @Test
    void rejectsInvalidConstPoolIndex() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(0); // cp
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(1);
            out.writeInt(BytecodeOpcode.CONST.code);
            out.writeInt(0); // target slot
            out.writeInt(123); // invalid cp index
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("cp") || ex.getMessage().toLowerCase().contains("constindex"));
    }

    @Test
    void rejectsConstPoolWrongTypeForGetStatic() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(1); // cp count
            out.writeByte(1); // LONG tag
            out.writeLong(1L);
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(1);
            out.writeInt(BytecodeOpcode.GET_STATIC.code);
            out.writeInt(0); // className idx (but it's LONG)
            out.writeInt(0); // fieldName idx (but it's LONG)
            out.writeInt(0); // target slot
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("string"));
    }

    @Test
    void rejectsInvalidCalleeIndexInsteadOfSkipping() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(0); // cp
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(1);
            out.writeInt(BytecodeOpcode.CALL.code);
            out.writeInt(999); // invalid callee
            out.writeInt(-1); out.writeInt(-1); out.writeInt(-1); out.writeInt(-1);
            out.writeInt(-1); // dest
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("callee"));
    }

    @Test
    void strictEntryRequiresMainFunction() throws Exception {
        String prev = System.getProperty("dhrlang.bytecode.strictEntry");
        System.setProperty("dhrlang.bytecode.strictEntry", "true");
        try {
            byte[] bytes;
            try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
                out.writeInt(0x44484243);
                out.writeInt(2);
                out.writeInt(0); // cp
                out.writeInt(1); // fn
                out.writeUTF("Foo.bar");
                out.writeInt(1);
                out.writeInt(BytecodeOpcode.RETURN.code);
                out.writeInt(-1);
                out.flush();
                bytes = baos.toByteArray();
            }
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
            assertTrue(ex.getMessage().toLowerCase().contains("entry"));
        } finally {
            if (prev == null) System.clearProperty("dhrlang.bytecode.strictEntry");
            else System.setProperty("dhrlang.bytecode.strictEntry", prev);
        }
    }

    @Test
    void untrustedModeEnforcesStrictEntryByDefault() throws Exception {
        String prevUntrusted = System.getProperty("dhrlang.bytecode.untrusted");
        String prevStrict = System.getProperty("dhrlang.bytecode.strictEntry");
        System.setProperty("dhrlang.bytecode.untrusted", "true");
        System.clearProperty("dhrlang.bytecode.strictEntry");
        try {
            byte[] bytes;
            try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
                out.writeInt(0x44484243);
                out.writeInt(2);
                out.writeInt(0); // cp
                out.writeInt(1); // fn
                out.writeUTF("Foo.bar");
                out.writeInt(1);
                out.writeInt(BytecodeOpcode.RETURN.code);
                out.writeInt(-1);
                out.flush();
                bytes = baos.toByteArray();
            }
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
            assertTrue(ex.getMessage().toLowerCase().contains("entry"));
        } finally {
            if (prevUntrusted == null) System.clearProperty("dhrlang.bytecode.untrusted");
            else System.setProperty("dhrlang.bytecode.untrusted", prevUntrusted);
            if (prevStrict == null) System.clearProperty("dhrlang.bytecode.strictEntry");
            else System.setProperty("dhrlang.bytecode.strictEntry", prevStrict);
        }
    }

    @Test
    void untrustedModeAllowsStrictEntryOverrideFalse() throws Exception {
        String prevUntrusted = System.getProperty("dhrlang.bytecode.untrusted");
        String prevStrict = System.getProperty("dhrlang.bytecode.strictEntry");
        System.setProperty("dhrlang.bytecode.untrusted", "true");
        System.setProperty("dhrlang.bytecode.strictEntry", "false");
        try {
            byte[] bytes;
            try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
                out.writeInt(0x44484243);
                out.writeInt(2);
                out.writeInt(0); // cp
                out.writeInt(1); // fn
                out.writeUTF("Foo.bar");
                out.writeInt(1);
                out.writeInt(BytecodeOpcode.RETURN.code);
                out.writeInt(-1);
                out.flush();
                bytes = baos.toByteArray();
            }
            assertDoesNotThrow(() -> new BytecodeVM().execute(bytes));
        } finally {
            if (prevUntrusted == null) System.clearProperty("dhrlang.bytecode.untrusted");
            else System.setProperty("dhrlang.bytecode.untrusted", prevUntrusted);
            if (prevStrict == null) System.clearProperty("dhrlang.bytecode.strictEntry");
            else System.setProperty("dhrlang.bytecode.strictEntry", prevStrict);
        }
    }

    @Test
    void rejectsTryPopUnderflowAtRuntime() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(0); // cp
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(1);
            out.writeInt(BytecodeOpcode.TRY_POP.code);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("try_pop") || ex.getMessage().toLowerCase().contains("handler"));
    }

    @Test
    void rejectsCatchEntryNotStartingWithCatchBind() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(1); // cp
            out.writeByte(3); // STRING
            out.writeUTF("any");
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(2);
            out.writeInt(BytecodeOpcode.TRY_PUSH.code);
            out.writeInt(1); // catchPc
            out.writeInt(0); // catchTypeIdx
            out.writeInt(BytecodeOpcode.RETURN.code);
            out.writeInt(-1);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("catch entry"));
    }

    @Test
    void rejectsNormalControlFlowEnteringCatchEntry() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(1); // cp
            out.writeByte(3); // STRING
            out.writeUTF("any");
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(3);
            out.writeInt(BytecodeOpcode.TRY_PUSH.code);
            out.writeInt(2); // catchPc
            out.writeInt(0); // catchTypeIdx
            out.writeInt(BytecodeOpcode.JUMP.code);
            out.writeInt(2); // normal jump into catch entry
            out.writeInt(BytecodeOpcode.CATCH_BIND.code);
            out.writeInt(0);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("reachable"));
    }

    @Test
    void rejectsInconsistentTryDepthOnMerge() throws Exception {
        byte[] bytes;
        try (var baos = new ByteArrayOutputStream(); var out = new DataOutputStream(baos)) {
            out.writeInt(0x44484243);
            out.writeInt(2);
            out.writeInt(2); // cp
            out.writeByte(3); out.writeUTF("any");
            out.writeByte(4); out.writeBoolean(false);
            out.writeInt(1); // fn
            out.writeUTF("Main.main");
            out.writeInt(6);
            // 0: TRY_PUSH catch=5 (unreachable normal-flow handler entry)
            out.writeInt(BytecodeOpcode.TRY_PUSH.code);
            out.writeInt(5);
            out.writeInt(0);
            // 1: CONST s0=false
            out.writeInt(BytecodeOpcode.CONST.code);
            out.writeInt(0);
            out.writeInt(1);
            // 2: JUMP_IF_FALSE s0 -> 4 (merge)
            out.writeInt(BytecodeOpcode.JUMP_IF_FALSE.code);
            out.writeInt(0);
            out.writeInt(4);
            // 3: TRY_POP (only on one path)
            out.writeInt(BytecodeOpcode.TRY_POP.code);
            // 4: RETURN (merge point)
            out.writeInt(BytecodeOpcode.RETURN.code);
            out.writeInt(-1);
            // 5: CATCH_BIND s0 (catch entry; unreachable because RETURN has no successor)
            out.writeInt(BytecodeOpcode.CATCH_BIND.code);
            out.writeInt(0);
            out.flush();
            bytes = baos.toByteArray();
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new BytecodeVM().execute(bytes));
        assertTrue(ex.getMessage().toLowerCase().contains("inconsistent"));
    }

    @Test
    void callReturnDoesNotSkipNextInstruction() {
        IrProgram p = new IrProgram();

        IrFunction foo = new IrFunction("Foo.f");
        foo.instructions.add(new IrConst(0, 41L));
        foo.instructions.add(new IrReturn(0));
        p.functions.add(foo);

        IrFunction main = new IrFunction("Main.main");
        main.instructions.add(new IrCall("Foo.f", new int[]{}, 10));
        main.instructions.add(new IrConst(11, 1L));
        main.instructions.add(new IrBinOp(IrBinOp.Op.ADD, 10, 11, 12));
        main.instructions.add(new IrPrint(12, true));
        main.instructions.add(new IrReturn(null));
        p.functions.add(main);

        assertEquals("42", runVm(p));
    }

    @Test
    void exceptionBubblesAcrossCallAndIsCatchable() {
        IrProgram p = new IrProgram();

        IrFunction thrower = new IrFunction("Foo.thrower");
        thrower.instructions.add(new IrConst(0, "boom"));
        thrower.instructions.add(new IrThrow(0));
        thrower.instructions.add(new IrReturn(null));
        p.functions.add(thrower);

        IrFunction main = new IrFunction("Main.main");
        main.instructions.add(new IrTryPush("catch", "any"));
        main.instructions.add(new IrCall("Foo.thrower", new int[]{}, -1));
        main.instructions.add(new IrTryPop());
        main.instructions.add(new IrConst(2, "NO"));
        main.instructions.add(new IrPrint(2, true));
        main.instructions.add(new IrJump("end"));

        main.instructions.add(new IrLabel("catch"));
        main.instructions.add(new IrCatchBind(1));
        main.instructions.add(new IrPrint(1, true));
        main.instructions.add(new IrLabel("end"));
        main.instructions.add(new IrReturn(null));
        p.functions.add(main);

        assertEquals("boom", runVm(p));
    }

    @Test
    void numericZeroIsTruthy() {
        IrProgram p = new IrProgram();
        IrFunction main = new IrFunction("Main.main");
        main.instructions.add(new IrConst(0, 0L));
        main.instructions.add(new IrJumpIfFalse(0, "L1"));
        main.instructions.add(new IrConst(1, "T"));
        main.instructions.add(new IrPrint(1, true));
        main.instructions.add(new IrJump("END"));
        main.instructions.add(new IrLabel("L1"));
        main.instructions.add(new IrConst(2, "F"));
        main.instructions.add(new IrPrint(2, true));
        main.instructions.add(new IrLabel("END"));
        main.instructions.add(new IrReturn(null));
        p.functions.add(main);
        assertEquals("T", runVm(p));
    }

    @Test
    void newArrayDefaultFillForNum() {
        IrProgram p = new IrProgram();
        IrFunction main = new IrFunction("Main.main");
        main.instructions.add(new IrConst(0, 3L));
        main.instructions.add(new IrNewArray(0, 1, "num"));
        main.instructions.add(new IrConst(2, 0L));
        main.instructions.add(new IrLoadElement(1, 2, 3));
        main.instructions.add(new IrPrint(3, true));
        main.instructions.add(new IrReturn(null));
        p.functions.add(main);
        assertEquals("0", runVm(p));
    }

    @Test
    void stepLimitAbortsInfiniteLoop() {
        String prev = System.getProperty("dhrlang.backend.maxSteps");
        System.setProperty("dhrlang.backend.maxSteps", "2000");
        try {
            IrProgram p = new IrProgram();
            IrFunction main = new IrFunction("Main.main");
            main.instructions.add(new IrLabel("L0"));
            main.instructions.add(new IrJump("L0"));
            p.functions.add(main);

            DhrRuntimeException ex = assertThrows(DhrRuntimeException.class, () -> runVm(p));
            assertTrue(ex.getMessage().contains("exceeded max instruction steps"));
        } finally {
            if (prev == null) System.clearProperty("dhrlang.backend.maxSteps");
            else System.setProperty("dhrlang.backend.maxSteps", prev);
        }
    }

    @Test
    void unresolvedLabelInIrIsRejectedByWriter() {
        IrProgram p = new IrProgram();
        IrFunction main = new IrFunction("Main.main");
        main.instructions.add(new IrJump("missing_label"));
        p.functions.add(main);

        assertThrows(IllegalArgumentException.class, () -> runVm(p));
    }
}
