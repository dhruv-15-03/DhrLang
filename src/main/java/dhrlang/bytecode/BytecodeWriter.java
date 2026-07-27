package dhrlang.bytecode;

import dhrlang.ir.*;
import java.io.*;
import java.util.*;

/** Serializes IR program to a simple DhrLang bytecode (.dbc). */
public class BytecodeWriter {
    private static final int MAGIC = 0x44484243; // 'DHBC'
    private static final int VERSION = 3;

    private static class ConstPool {
        final Map<Object,Integer> indexMap = new HashMap<>();
        final List<Object> entries = new ArrayList<>();
        int indexOf(Object v){
            if(v==null) v = NullConst.INSTANCE;
            Integer idx = indexMap.get(v);
            if(idx!=null) return idx;
            int ni = entries.size();
            entries.add(v);
            indexMap.put(v, ni);
            return ni;
        }
    }
            private enum Tag{ NULL, LONG, DOUBLE, STRING, BOOLEAN }
    private enum NullConst{ INSTANCE }

    public byte[] write(IrProgram program){
        try{
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(MAGIC); out.writeInt(VERSION);
            // We will build constant pool while walking functions
            ConstPool cp = new ConstPool();
            // First pass: collect constants, function indices, and compute label positions per function
            List<Map<String,Integer>> labelPositions = new ArrayList<>();
            Map<String,Integer> functionIndex = new HashMap<>();
            for(int i=0;i<program.functions.size();i++){
                functionIndex.put(program.functions.get(i).name, i);
            }
            for(IrFunction f: program.functions){
                Map<String,Integer> map = new HashMap<>();
                int pc = 0;
                for(IrInstruction ins: f.instructions){
                    if(ins instanceof IrConst c){ cp.indexOf(c.value); pc++; }
                    else if(ins instanceof IrLabel lab){ map.put(lab.name, pc); }
                    else if(ins instanceof IrCallNative cn){ cp.indexOf(cn.functionName); pc++; }
                    else if(ins instanceof IrNewObject no){ cp.indexOf(no.className); pc++; }
                    else if(ins instanceof IrInvokeMethod im){ cp.indexOf(im.methodName); pc++; }
                    else if(ins instanceof IrGetStatic gs){ cp.indexOf(gs.className); cp.indexOf(gs.fieldName); pc++; }
                    else if(ins instanceof IrSetStatic ss){ cp.indexOf(ss.className); cp.indexOf(ss.fieldName); pc++; }
                    else if(ins instanceof IrGetField gf){ cp.indexOf(gf.fieldName); pc++; }
                    else if(ins instanceof IrSetField sf){ cp.indexOf(sf.fieldName); pc++; }
                    else if(ins instanceof IrNewArray na){ if(na.elementType!=null) cp.indexOf(na.elementType); pc++; }
                    else if(ins instanceof IrTryPush tp){ cp.indexOf(tp.catchType); pc++; }
                    else if(ins instanceof IrTryPop){ pc++; }
                    else if(ins instanceof IrThrow th){ pc++; }
                    else if(ins instanceof IrCatchBind cb){ pc++; }
                    else if(!(ins instanceof IrLabel)){ pc++; }
                }
                labelPositions.add(map);
            }
            // Write constant pool
            out.writeInt(cp.entries.size());
            for(Object e: cp.entries){
                if(e==NullConst.INSTANCE){ out.writeByte(Tag.NULL.ordinal()); }
                else if(e instanceof Long l){ out.writeByte(Tag.LONG.ordinal()); out.writeLong(l); }
                else if(e instanceof Integer i){ out.writeByte(Tag.LONG.ordinal()); out.writeLong(i.longValue()); }
                else if(e instanceof Double d){ out.writeByte(Tag.DOUBLE.ordinal()); out.writeDouble(d); }
                else if(e instanceof String s){ out.writeByte(Tag.STRING.ordinal()); out.writeUTF(s); }
                else if(e instanceof Boolean b){ out.writeByte(Tag.BOOLEAN.ordinal()); out.writeBoolean(b); }
                else { // fallback stringify
                    out.writeByte(Tag.STRING.ordinal()); out.writeUTF(String.valueOf(e));
                }
            }
            // Write functions
            // Write class definitions for OOP runtime
            out.writeInt(program.classes.size());
            for(IrClassDef cls : program.classes){
                out.writeUTF(cls.name);
                out.writeUTF(cls.superclassName == null ? "" : cls.superclassName);
                out.writeInt(cls.instanceFields.size());
                for(IrFieldDef field : cls.instanceFields){
                    out.writeUTF(field.name);
                    out.writeUTF(field.type == null ? "" : field.type);
                }
            }
            out.writeInt(program.functions.size());
            for(int fi=0; fi<program.functions.size(); fi++){
                IrFunction f = program.functions.get(fi);
                out.writeUTF(f.name);
                out.writeUTF(f.ownerClassName == null ? "" : f.ownerClassName);
                out.writeUTF(f.simpleName == null ? "" : f.simpleName);
                out.writeBoolean(f.isStatic);
                // Count non-label instructions
                int count = 0; for(IrInstruction ins: f.instructions){ if(!(ins instanceof IrLabel)) count++; }
                out.writeInt(count);
                Map<String,Integer> labelPc = labelPositions.get(fi);
                for(IrInstruction ins: f.instructions){
                    if(ins instanceof IrLabel) continue;
                    if(ins instanceof IrConst c){
                        out.writeInt(BytecodeOpcode.CONST.code);
                        out.writeInt(c.targetSlot); out.writeInt(cp.indexOf(c.value));
                    } else if(ins instanceof IrLoadLocal ll){
                        out.writeInt(BytecodeOpcode.LOAD_LOCAL.code);
                        out.writeInt(ll.slot); out.writeInt(ll.targetSlot);
                    } else if(ins instanceof IrStoreLocal sl){
                        out.writeInt(BytecodeOpcode.STORE_LOCAL.code);
                        out.writeInt(sl.sourceSlot); out.writeInt(sl.destSlot);
                    } else if(ins instanceof IrBinOp b){
                        BytecodeOpcode op = switch(b.op){
                            case ADD -> BytecodeOpcode.ADD;
                            case SUB -> BytecodeOpcode.SUB;
                            case MUL -> BytecodeOpcode.MUL;
                            case DIV -> BytecodeOpcode.DIV;
                            case MOD -> BytecodeOpcode.MOD;
                            case BIT_AND -> BytecodeOpcode.BIT_AND;
                            case BIT_OR -> BytecodeOpcode.BIT_OR;
                            case BIT_XOR -> BytecodeOpcode.BIT_XOR;
                            case LSHIFT -> BytecodeOpcode.LSHIFT;
                            case RSHIFT -> BytecodeOpcode.RSHIFT;
                        };
                        out.writeInt(op.code);
                        out.writeInt(b.leftSlot); out.writeInt(b.rightSlot); out.writeInt(b.targetSlot);
                    } else if(ins instanceof IrCompare cmp){
                        BytecodeOpcode op = switch(cmp.op){
                            case EQ->BytecodeOpcode.EQ; case NEQ->BytecodeOpcode.NEQ; case LT->BytecodeOpcode.LT; case LE->BytecodeOpcode.LE; case GT->BytecodeOpcode.GT; case GE->BytecodeOpcode.GE; };
                        out.writeInt(op.code);
                        out.writeInt(cmp.leftSlot); out.writeInt(cmp.rightSlot); out.writeInt(cmp.targetSlot);
                    } else if(ins instanceof IrJump j){
                        out.writeInt(BytecodeOpcode.JUMP.code);
                        Integer target = labelPc.get(j.label);
                        if(target == null) throw new IllegalArgumentException("Unresolved label in function "+f.name+": "+j.label);
                        out.writeInt(target);
                    } else if(ins instanceof IrJumpIfFalse jf){
                        out.writeInt(BytecodeOpcode.JUMP_IF_FALSE.code);
                        Integer target = labelPc.get(jf.label);
                        if(target == null) throw new IllegalArgumentException("Unresolved label in function "+f.name+": "+jf.label);
                        out.writeInt(jf.condSlot); out.writeInt(target);
                    } else if(ins instanceof IrPrint p){
                        out.writeInt(BytecodeOpcode.PRINT.code);
                        out.writeInt(p.slot); out.writeBoolean(p.newline);
                    } else if(ins instanceof IrReturn r){
                        out.writeInt(BytecodeOpcode.RETURN.code);
                        out.writeInt(r.slot==null?-1:r.slot);
                    } else if(ins instanceof IrUnaryOp u){
                        BytecodeOpcode op = switch(u.op){
                            case NEG -> BytecodeOpcode.NEG;
                            case NOT -> BytecodeOpcode.NOT;
                            case BIT_NOT -> BytecodeOpcode.BIT_NOT;
                        };
                        out.writeInt(op.code);
                        out.writeInt(u.sourceSlot); out.writeInt(u.targetSlot);
                    } else if(ins instanceof IrNewArray na){
                        out.writeInt(BytecodeOpcode.NEW_ARRAY.code);
                        out.writeInt(na.sizeSlot); out.writeInt(na.targetSlot);
                        out.writeInt(na.elementType==null? -1 : cp.indexOf(na.elementType));
                    } else if(ins instanceof IrLoadElement le){
                        out.writeInt(BytecodeOpcode.LOAD_ELEM.code);
                        out.writeInt(le.arraySlot); out.writeInt(le.indexSlot); out.writeInt(le.targetSlot);
                    } else if(ins instanceof IrStoreElement se){
                        out.writeInt(BytecodeOpcode.STORE_ELEM.code);
                        out.writeInt(se.arraySlot); out.writeInt(se.indexSlot); out.writeInt(se.valueSlot);
                    } else if(ins instanceof IrArrayLength al){
                        out.writeInt(BytecodeOpcode.ARRAY_LENGTH.code);
                        out.writeInt(al.arraySlot); out.writeInt(al.targetSlot);
                    } else if(ins instanceof IrCall call){
                        int idx = functionIndex.getOrDefault(call.functionName, -1);
                        int n = call.argSlots.length;
                        if(n <= 4){
                            out.writeInt(BytecodeOpcode.CALL.code);
                            int a0=-1,a1=-1,a2=-1,a3=-1;
                            if(n>0) a0 = call.argSlots[0]; if(n>1) a1 = call.argSlots[1]; if(n>2) a2 = call.argSlots[2]; if(n>3) a3 = call.argSlots[3];
                            out.writeInt(idx); out.writeInt(a0); out.writeInt(a1); out.writeInt(a2); out.writeInt(a3); out.writeInt(call.destSlot);
                        } else {
                            out.writeInt(BytecodeOpcode.CALL_VAR.code);
                            out.writeInt(idx);
                            out.writeInt(n);
                            for(int s : call.argSlots){ out.writeInt(s); }
                            out.writeInt(call.destSlot);
                        }
                    } else if(ins instanceof IrCallNative nativeCall){
                        out.writeInt(BytecodeOpcode.CALL_NATIVE.code);
                        out.writeInt(cp.indexOf(nativeCall.functionName));
                        out.writeInt(nativeCall.argSlots.length);
                        for(int s : nativeCall.argSlots){ out.writeInt(s); }
                        out.writeInt(nativeCall.destSlot);
                    } else if(ins instanceof IrGetStatic gs){
                        out.writeInt(BytecodeOpcode.GET_STATIC.code);
                        out.writeInt(cp.indexOf(gs.className));
                        out.writeInt(cp.indexOf(gs.fieldName));
                        out.writeInt(gs.targetSlot);
                    } else if(ins instanceof IrSetStatic ss){
                        out.writeInt(BytecodeOpcode.SET_STATIC.code);
                        out.writeInt(cp.indexOf(ss.className));
                        out.writeInt(cp.indexOf(ss.fieldName));
                        out.writeInt(ss.valueSlot);
                    } else if(ins instanceof IrGetField gf){
                        out.writeInt(BytecodeOpcode.GET_FIELD.code);
                        out.writeInt(gf.objectSlot);
                        out.writeInt(cp.indexOf(gf.fieldName));
                        out.writeInt(gf.targetSlot);
                    } else if(ins instanceof IrSetField sf){
                        out.writeInt(BytecodeOpcode.SET_FIELD.code);
                        out.writeInt(sf.objectSlot);
                        out.writeInt(cp.indexOf(sf.fieldName));
                        out.writeInt(sf.valueSlot);
                    } else if(ins instanceof IrTryPush tp){
                        out.writeInt(BytecodeOpcode.TRY_PUSH.code);
                        Integer target = labelPc.get(tp.catchLabel);
                        if(target == null) throw new IllegalArgumentException("Unresolved label in function "+f.name+": "+tp.catchLabel);
                        out.writeInt(target);
                        out.writeInt(cp.indexOf(tp.catchType));
                    } else if(ins instanceof IrTryPop){
                        out.writeInt(BytecodeOpcode.TRY_POP.code);
                    } else if(ins instanceof IrThrow th){
                        out.writeInt(BytecodeOpcode.THROW.code);
                        out.writeInt(th.valueSlot);
                    } else if(ins instanceof IrCatchBind cb){
                        out.writeInt(BytecodeOpcode.CATCH_BIND.code);
                        out.writeInt(cb.targetSlot);
                    } else if(ins instanceof IrNewObject no){
                        out.writeInt(BytecodeOpcode.NEW_OBJ.code);
                        out.writeInt(cp.indexOf(no.className));
                        out.writeInt(no.targetSlot);
                    } else if(ins instanceof IrInvokeMethod im){
                        out.writeInt(BytecodeOpcode.INVOKE_METHOD.code);
                        out.writeInt(im.objectSlot);
                        out.writeInt(cp.indexOf(im.methodName));
                        out.writeInt(im.argSlots.length);
                        for(int s : im.argSlots){ out.writeInt(s); }
                        out.writeInt(im.destSlot);
                    } else {
                        throw new IllegalArgumentException(
                            "BytecodeWriter: unsupported IR instruction: "
                            + ins.getClass().getSimpleName() + " in function " + f.name);
                    }
                }
            }
            out.flush();
            return baos.toByteArray();
        }catch(IOException e){ throw new RuntimeException(e); }
    }
}
