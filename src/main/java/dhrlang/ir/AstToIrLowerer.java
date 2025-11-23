package dhrlang.ir;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;

/** Very small subset lowering (Phase 1 slice): literals, var decls with literal init, addition, return void. */
public class AstToIrLowerer {
    private final ErrorReporter errorReporter;
    public AstToIrLowerer(ErrorReporter er){ this.errorReporter = er; }

    public IrProgram lower(Program program){
        IrProgram ir = new IrProgram();
        // Add Main.main first (entry)
        for(ClassDecl cd: program.getClasses()){
            if(!cd.getName().equals("Main")) continue;
            cd.getFunctions().stream().filter(m-> m.getName().equals("main")).findFirst()
                    .ifPresent(f-> ir.functions.add(lowerFunction(f, cd.getName())));
            break;
        }
        // Add all other static functions across classes (including Main.* other than main)
        for(ClassDecl cd: program.getClasses()){
            for(FunctionDecl f: cd.getFunctions()){
                if(f.getName().equals("main")) continue; // already added above
                if(f.hasModifier(dhrlang.ast.Modifier.STATIC)){
                    ir.functions.add(lowerFunction(f, cd.getName()));
                }
            }
        }
        return ir;
    }

    private IrFunction lowerFunction(FunctionDecl f, String currentClass){
        IrFunction irf = new IrFunction(currentClass + "." + f.getName());
        LoweringContext ctx = new LoweringContext();
        // Allocate slots for parameters in order so they map to slots[0..n-1]
        if(f.getParameters()!=null){
            for(VarDecl p: f.getParameters()){
                ctx.allocSlot(p.getName());
            }
        }
        for(Statement s: f.getBody().getStatements()){
            lowerStmt(s, irf, ctx, currentClass);
        }
        if(irf.instructions.isEmpty() || !(irf.instructions.get(irf.instructions.size()-1) instanceof IrReturn)) {
            irf.instructions.add(new IrReturn(null));
        }
        return irf;
    }

    private void lowerStmt(Statement s, IrFunction out, LoweringContext ctx, String currentClass){
        if(s instanceof VarDecl vd){
            int slot = ctx.allocSlot(vd.getName());
            if(vd.getInitializer()!=null){
                int val = lowerExpr(vd.getInitializer(), out, ctx, currentClass);
                out.instructions.add(new IrStoreLocal(val, slot));
            }
        } else if(s instanceof ExpressionStmt es){
            lowerExpr(es.getExpression(), out, ctx, currentClass);
        } else if(s instanceof PrintStmt ps){
            int v = lowerExpr(ps.getExpression(), out, ctx, currentClass);
            out.instructions.add(new IrPrint(v,true));
        } else if(s instanceof IfStmt is){
            int cond = lowerExpr(is.getCondition(), out, ctx, currentClass);
            String elseL = freshLabel("else");
            String endL = freshLabel("endif");
            if(is.getElseBranch()!=null){
                out.instructions.add(new IrJumpIfFalse(cond, elseL));
                lowerStmt(is.getThenBranch(), out, ctx, currentClass);
                out.instructions.add(new IrJump(endL));
                out.instructions.add(new IrLabel(elseL));
                lowerStmt(is.getElseBranch(), out, ctx, currentClass);
                out.instructions.add(new IrLabel(endL));
            } else {
                out.instructions.add(new IrJumpIfFalse(cond, endL));
                lowerStmt(is.getThenBranch(), out, ctx, currentClass);
                out.instructions.add(new IrLabel(endL));
            }
        } else if(s instanceof WhileStmt ws){
            String loopL = freshLabel("loop");
            String endL = freshLabel("endloop");
            out.instructions.add(new IrLabel(loopL));
            // Push loop labels for break/continue within body
            ctx.pushLoop(loopL, endL);
            int cond = lowerExpr(ws.getCondition(), out, ctx, currentClass);
            out.instructions.add(new IrJumpIfFalse(cond, endL));
            lowerStmt(ws.getBody(), out, ctx, currentClass);
            // Pop after body lowering
            ctx.popLoop();
            out.instructions.add(new IrJump(loopL));
            out.instructions.add(new IrLabel(endL));
        } else if(s instanceof Block blk){
            for(Statement st: blk.getStatements()){
                lowerStmt(st, out, ctx, currentClass);
            }
        } else if(s instanceof BreakStmt){
            String br = ctx.currentBreak();
            if(br != null){ out.instructions.add(new IrJump(br)); }
        } else if(s instanceof ContinueStmt){
            String cont = ctx.currentContinue();
            if(cont != null){ out.instructions.add(new IrJump(cont)); }
        } else if(s instanceof TryStmt ts){
            String endL = freshLabel("try_end");
            int catchCount = (ts.getCatchClauses()==null)? 0 : ts.getCatchClauses().size();
            String[] catchLabels = new String[catchCount];
            // Pre-create labels for each catch
            for(int i=0;i<catchCount;i++) catchLabels[i] = freshLabel("catch"+i);
            // Push handlers for each catch in reverse order so first clause has highest priority
            for(int i=catchCount-1;i>=0;i--){
                CatchClause cc = ts.getCatchClauses().get(i);
                String ctype = cc.getExceptionType()!=null? cc.getExceptionType() : "any";
                out.instructions.add(new IrTryPush(catchLabels[i], ctype));
            }
            // Lower try block
            lowerStmt(ts.getTryBlock(), out, ctx, currentClass);
            // Pop all handlers on normal exit
            for(int i=0;i<catchCount;i++) out.instructions.add(new IrTryPop());
            // finally on normal flow
            if(ts.getFinallyBlock()!=null){ lowerStmt(ts.getFinallyBlock(), out, ctx, currentClass); }
            out.instructions.add(new IrJump(endL));
            // Emit each catch block
            for(int i=0;i<catchCount;i++){
                CatchClause cc = ts.getCatchClauses().get(i);
                out.instructions.add(new IrLabel(catchLabels[i]));
                // Bind exception to catch parameter slot
                String pname = cc.getParameter()!=null? cc.getParameter() : "e";
                int slot = ctx.getSlot(pname); if(slot<0) slot = ctx.allocSlot(pname);
                out.instructions.add(new IrCatchBind(slot));
                // Lower catch body
                lowerStmt(cc.getBody(), out, ctx, currentClass);
                // finally after catch
                if(ts.getFinallyBlock()!=null){ lowerStmt(ts.getFinallyBlock(), out, ctx, currentClass); }
                out.instructions.add(new IrJump(endL));
            }
            out.instructions.add(new IrLabel(endL));
        } else if(s instanceof ThrowStmt th){
            int v = lowerExpr(th.getValue(), out, ctx, currentClass);
            out.instructions.add(new IrThrow(v));
        }
    }

    private int lowerExpr(Expression e, IrFunction out, LoweringContext ctx, String currentClass){
        if(e instanceof StaticAccessExpr sae){
            String cls = sae.className.getName().getLexeme();
            String mem = sae.memberName.getLexeme();
            int t = ctx.newTemp();
            out.instructions.add(new IrGetStatic(cls, mem, t));
            return t;
        } else if(e instanceof StaticAssignExpr sassign){
            String cls = sassign.className.getName().getLexeme();
            String mem = sassign.memberName.getLexeme();
            int v = lowerExpr(sassign.value, out, ctx, currentClass);
            out.instructions.add(new IrSetStatic(cls, mem, v));
            return v;
        } else if(e instanceof GetExpr ge){
            int obj = lowerExpr(ge.getObject(), out, ctx, currentClass);
            String name = ge.getName().getLexeme();
            int t = ctx.newTemp();
            out.instructions.add(new IrGetField(obj, name, t));
            return t;
        } else if(e instanceof SetExpr se){
            int obj = lowerExpr(se.getObject(), out, ctx, currentClass);
            int val = lowerExpr(se.getValue(), out, ctx, currentClass);
            String name = se.getName().getLexeme();
            out.instructions.add(new IrSetField(obj, name, val));
            return val;
        }
        if(e instanceof LiteralExpr le){
            int t = ctx.newTemp();
            out.instructions.add(new IrConst(t, literalValue(le)));
            return t;
        } else if(e instanceof ArrayExpr arr){
            // Lower array literal by allocating and storing each element
            java.util.List<Expression> elems = arr.getElements();
            int size = ctx.newTemp(); out.instructions.add(new IrConst(size, (long) elems.size()));
            int arrSlot = ctx.newTemp(); out.instructions.add(new IrNewArray(size, arrSlot));
            for(int i=0;i<elems.size();i++){
                int idx = ctx.newTemp(); out.instructions.add(new IrConst(idx, (long) i));
                int val = lowerExpr(elems.get(i), out, ctx, currentClass);
                out.instructions.add(new IrStoreElement(arrSlot, idx, val));
            }
            return arrSlot;
        } else if(e instanceof NewArrayExpr na){
            // Only support first dimension now
            java.util.List<Expression> sizes = na.getSizes();
            Expression sExpr = (sizes!=null && !sizes.isEmpty())? sizes.get(0): na.getSize();
            int sz = lowerExpr(sExpr, out, ctx, currentClass);
            int arrSlot = ctx.newTemp(); out.instructions.add(new IrNewArray(sz, arrSlot));
            return arrSlot;
        } else if(e instanceof IndexExpr ie){
            int arrS = lowerExpr(ie.getObject(), out, ctx, currentClass);
            int idxS = lowerExpr(ie.getIndex(), out, ctx, currentClass);
            int t = ctx.newTemp();
            out.instructions.add(new IrLoadElement(arrS, idxS, t));
            return t;
        } else if(e instanceof IndexAssignExpr ia){
            int arrS = lowerExpr(ia.getObject(), out, ctx, currentClass);
            int idxS = lowerExpr(ia.getIndex(), out, ctx, currentClass);
            int valS = lowerExpr(ia.getValue(), out, ctx, currentClass);
            out.instructions.add(new IrStoreElement(arrS, idxS, valS));
            // Expression value is the assigned value
            return valS;
        } else if(e instanceof CallExpr ce){
            // Lower selected native calls directly: print/printLine/arrayLength
            Expression callee = ce.getCallee();
            java.util.List<Expression> args = ce.getArguments();
            if(callee instanceof VariableExpr ve){
                String name = ve.getName()!=null? ve.getName().getLexeme(): "";
                if(("print".equals(name) || "printLine".equals(name)) && args.size()==1){
                    int v = lowerExpr(args.get(0), out, ctx, currentClass);
                    out.instructions.add(new IrPrint(v, "printLine".equals(name)));
                    int t = ctx.newTemp(); out.instructions.add(new IrConst(t, null));
                    return t; // calls return void -> null
                }
                if("arrayLength".equals(name) && args.size()==1){
                    int arrS = lowerExpr(args.get(0), out, ctx, currentClass);
                    int t = ctx.newTemp(); out.instructions.add(new IrArrayLength(arrS, t));
                    return t;
                }
                // Attempt to lower a user-defined function call in the same class (up to 4 args)
                String qn = name.contains(".")? name : (currentClass + "." + name);
                int argc = Math.min(args.size(), 4);
                int[] argSlots = new int[argc];
                for(int i=0;i<argc;i++) argSlots[i] = lowerExpr(args.get(i), out, ctx, currentClass);
                int dest = ctx.newTemp();
                out.instructions.add(new IrCall(qn, argSlots, dest));
                return dest;
            } else if(callee instanceof StaticAccessExpr sae){
                // Static method call: ClassName.method(args...)
                String cls = sae.className.getName().getLexeme();
                String mem = sae.memberName.getLexeme();
                String qn = cls + "." + mem;
                int argc = Math.min(args.size(), 4);
                int[] argSlots = new int[argc];
                for(int i=0;i<argc;i++) argSlots[i] = lowerExpr(args.get(i), out, ctx, currentClass);
                int dest = ctx.newTemp();
                out.instructions.add(new IrCall(qn, argSlots, dest));
                return dest;
            }
            // Unsupported call for now: yield null
            int t = ctx.newTemp(); out.instructions.add(new IrConst(t, null)); return t;
        } else if(e instanceof AssignmentExpr ae){
            // Lower RHS then store into existing local slot if present.
            int valueSlot = lowerExpr(ae.getValue(), out, ctx, currentClass);
            String name = ae.getName()!=null? ae.getName().getLexeme(): "";
            int slot = ctx.getSlot(name);
            if(slot>=0){
                out.instructions.add(new IrStoreLocal(valueSlot, slot));
            } else {
                // If slot does not exist yet, allocate (fallback) then store.
                slot = ctx.allocSlot(name);
                out.instructions.add(new IrStoreLocal(valueSlot, slot));
            }
            // For expression value semantics, produce a temp copy of stored value.
            int t = ctx.newTemp();
            out.instructions.add(new IrLoadLocal(slot, t));
            return t;
        } else if(e instanceof PostfixIncrementExpr pie){
            // Support only simple variable targets for now
            Expression target = pie.getTarget();
            if(target instanceof VariableExpr ve){
                String name = ve.getName()!=null? ve.getName().getLexeme(): "";
                int slot = ctx.getSlot(name);
                if(slot < 0){
                    slot = ctx.allocSlot(name);
                }
                int oldVal = ctx.newTemp();
                out.instructions.add(new IrLoadLocal(slot, oldVal));
                int one = ctx.newTemp();
                out.instructions.add(new IrConst(one, 1L));
                int newVal = ctx.newTemp();
                if(pie.isIncrement()){
                    out.instructions.add(new IrBinOp(IrBinOp.Op.ADD, oldVal, one, newVal));
                } else {
                    out.instructions.add(new IrBinOp(IrBinOp.Op.SUB, oldVal, one, newVal));
                }
                out.instructions.add(new IrStoreLocal(newVal, slot));
                // Postfix returns old value
                return oldVal;
            } else {
                int t = ctx.newTemp();
                out.instructions.add(new IrConst(t, null));
                return t;
            }
        } else if(e instanceof BinaryExpr be){
            String op = be.getOperator().getLexeme();
            if("&&".equals(op)){
                int t = ctx.newTemp();
                int l = lowerExpr(be.getLeft(), out, ctx, currentClass);
                String falseL = freshLabel("and_false");
                String endL = freshLabel("and_end");
                out.instructions.add(new IrJumpIfFalse(l, falseL));
                int r = lowerExpr(be.getRight(), out, ctx, currentClass);
                out.instructions.add(new IrJumpIfFalse(r, falseL));
                out.instructions.add(new IrConst(t, Boolean.TRUE));
                out.instructions.add(new IrJump(endL));
                out.instructions.add(new IrLabel(falseL));
                out.instructions.add(new IrConst(t, Boolean.FALSE));
                out.instructions.add(new IrLabel(endL));
                return t;
            } else if("||".equals(op)){
                int t = ctx.newTemp();
                int l = lowerExpr(be.getLeft(), out, ctx, currentClass);
                String rightL = freshLabel("or_right");
                String trueL = freshLabel("or_true");
                String falseL = freshLabel("or_false");
                String endL = freshLabel("or_end");
                // If left is false -> evaluate right; else true
                out.instructions.add(new IrJumpIfFalse(l, rightL));
                out.instructions.add(new IrJump(trueL));
                out.instructions.add(new IrLabel(rightL));
                int r = lowerExpr(be.getRight(), out, ctx, currentClass);
                out.instructions.add(new IrJumpIfFalse(r, falseL));
                out.instructions.add(new IrJump(trueL));
                out.instructions.add(new IrLabel(falseL));
                out.instructions.add(new IrConst(t, Boolean.FALSE));
                out.instructions.add(new IrJump(endL));
                out.instructions.add(new IrLabel(trueL));
                out.instructions.add(new IrConst(t, Boolean.TRUE));
                out.instructions.add(new IrLabel(endL));
                return t;
            } else {
                int l = lowerExpr(be.getLeft(), out, ctx, currentClass);
                int r = lowerExpr(be.getRight(), out, ctx, currentClass);
                int t = ctx.newTemp();
                switch(op){
                    case "+" -> out.instructions.add(new IrBinOp(IrBinOp.Op.ADD, l, r, t));
                    case "-" -> out.instructions.add(new IrBinOp(IrBinOp.Op.SUB, l, r, t));
                    case "*" -> out.instructions.add(new IrBinOp(IrBinOp.Op.MUL, l, r, t));
                    case "/" -> out.instructions.add(new IrBinOp(IrBinOp.Op.DIV, l, r, t));
                    case "==" -> out.instructions.add(new IrCompare(IrCompare.Op.EQ, l, r, t));
                    case "!=" -> out.instructions.add(new IrCompare(IrCompare.Op.NEQ, l, r, t));
                    case "<" -> out.instructions.add(new IrCompare(IrCompare.Op.LT, l, r, t));
                    case "<=" -> out.instructions.add(new IrCompare(IrCompare.Op.LE, l, r, t));
                    case ">" -> out.instructions.add(new IrCompare(IrCompare.Op.GT, l, r, t));
                    case ">=" -> out.instructions.add(new IrCompare(IrCompare.Op.GE, l, r, t));
                    default -> out.instructions.add(new IrConst(t, null));
                }
                return t;
            }
        } else if(e instanceof UnaryExpr ue){
            int inner = lowerExpr(ue.getRight(), out, ctx, currentClass);
            int t = ctx.newTemp();
            String op = ue.getOperator()!=null? ue.getOperator().getLexeme(): "";
            switch(op){
                case "-" -> out.instructions.add(new IrUnaryOp(IrUnaryOp.Op.NEG, inner, t));
                case "!" -> out.instructions.add(new IrUnaryOp(IrUnaryOp.Op.NOT, inner, t));
                default -> out.instructions.add(new IrConst(t, null));
            }
            return t;
        } else if(e instanceof PostfixIncrementExpr pie){
            // Only support variable targets for now
            Expression tgt = pie.getTarget();
            if(tgt instanceof VariableExpr ve){
                String name = ve.getName()!=null? ve.getName().getLexeme(): "";
                int slot = ctx.getSlot(name);
                if(slot<0){ slot = ctx.allocSlot(name); out.instructions.add(new IrConst(slot, 0)); }
                int original = ctx.newTemp();
                out.instructions.add(new IrLoadLocal(slot, original));
                int one = ctx.newTemp();
                out.instructions.add(new IrConst(one, 1));
                int updated = ctx.newTemp();
                if(pie.isIncrement()){
                    out.instructions.add(new IrBinOp(IrBinOp.Op.ADD, original, one, updated));
                } else {
                    out.instructions.add(new IrBinOp(IrBinOp.Op.SUB, original, one, updated));
                }
                out.instructions.add(new IrStoreLocal(updated, slot));
                // Postfix yields original value
                return original;
            } else {
                int t = ctx.newTemp();
                out.instructions.add(new IrConst(t, null));
                return t;
            }
        } else if(e instanceof VariableExpr ve){
            String name = ve.getName()!=null? ve.getName().getLexeme(): "";
            int slot = ctx.getSlot(name);
            int t = ctx.newTemp();
            if(slot>=0) out.instructions.add(new IrLoadLocal(slot, t));
            else out.instructions.add(new IrConst(t, null));
            return t;
        }
        return ctx.newTemp(); // placeholder slot allocation for unsupported exprs
    }

    private Object literalValue(LiteralExpr le){
        return le.getValue();
    }

    private int labelCounter = 0;
    private String freshLabel(String prefix){ return prefix+"_"+(labelCounter++); }
}
