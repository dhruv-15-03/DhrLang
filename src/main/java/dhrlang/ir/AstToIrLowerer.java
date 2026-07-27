package dhrlang.ir;

import dhrlang.ast.*;
import dhrlang.error.ErrorFactory;
import dhrlang.error.ErrorReporter;

import java.util.HashMap;
import java.util.Map;

/** Very small subset lowering (Phase 1 slice): literals, var decls with literal init, addition, return void. */
public class AstToIrLowerer {
    private final ErrorReporter errorReporter;
    private final Map<String, ClassDecl> classIndex = new HashMap<>();
    public AstToIrLowerer(ErrorReporter er){ this.errorReporter = er; }

    public IrProgram lower(Program program){
        IrProgram ir = new IrProgram();
        classIndex.clear();
        for (ClassDecl cd : program.getClasses()) {
            classIndex.put(cd.getName(), cd);
            IrClassDef classDef = new IrClassDef(
                    cd.getName(),
                    cd.getSuperclass() != null ? cd.getSuperclass().getName().getLexeme() : null
            );
            for (VarDecl field : cd.getVariables()) {
                if (!field.hasModifier(Modifier.STATIC)) {
                    classDef.instanceFields.add(new IrFieldDef(field.getName(), field.getType()));
                }
            }
            ir.classes.add(classDef);
        }
        // Select an entrypoint and add it first.
        // Prefer Main.main if present; otherwise first static *.main.
        String entryQualifiedName = null;

        ClassDecl preferredMainClass = null;
        for(ClassDecl cd: program.getClasses()){
            if("Main".equals(cd.getName())){ preferredMainClass = cd; break; }
        }
        if(preferredMainClass != null){
            for(FunctionDecl f : preferredMainClass.getFunctions()){
                if("main".equals(f.getName()) && f.hasModifier(dhrlang.ast.Modifier.STATIC)){
                    IrFunction irEntry = lowerFunction(f, preferredMainClass.getName(), true);
                    entryQualifiedName = irEntry.name;
                    ir.functions.add(irEntry);
                    break;
                }
            }
        }
        if(entryQualifiedName == null){
            outer:
            for(ClassDecl cd: program.getClasses()){
                for(FunctionDecl f : cd.getFunctions()){
                    if("main".equals(f.getName()) && f.hasModifier(dhrlang.ast.Modifier.STATIC)){
                        IrFunction irEntry = lowerFunction(f, cd.getName(), true);
                        entryQualifiedName = irEntry.name;
                        ir.functions.add(irEntry);
                        break outer;
                    }
                }
            }
        }

        // Add all remaining functions across classes, including instance methods.
        for(ClassDecl cd: program.getClasses()){
            for(FunctionDecl f: cd.getFunctions()){
                IrFunction lowered = lowerFunction(f, cd.getName(), f.hasModifier(dhrlang.ast.Modifier.STATIC));
                if(entryQualifiedName != null && entryQualifiedName.equals(lowered.name)) continue;
                ir.functions.add(lowered);
            }
        }

        // Generate static field initializers and prepend to the entry function.
        // We use a temporary IrFunction to collect the init instructions (with their own
        // slot numbering), then prepend them to the real entry function's body.
        if(!ir.functions.isEmpty()){
            IrFunction entry = ir.functions.get(0);
            IrFunction initFunc = new IrFunction("$staticInit", null, "$staticInit", true);
            LoweringContext initCtx = new LoweringContext();
            boolean hasStaticInits = false;
            for(ClassDecl cd : program.getClasses()){
                for(VarDecl field : cd.getVariables()){
                    if(field.hasModifier(Modifier.STATIC) && field.getInitializer() != null){
                        int val = lowerExpr(field.getInitializer(), initFunc, initCtx, cd.getName());
                        initFunc.instructions.add(new IrSetStatic(cd.getName(), field.getName(), val));
                        hasStaticInits = true;
                    }
                }
            }
            if(hasStaticInits){
                initFunc.instructions.addAll(entry.instructions);
                entry.instructions.clear();
                entry.instructions.addAll(initFunc.instructions);
            }
        }

        return ir;
    }

    private IrFunction lowerFunction(FunctionDecl f, String currentClass, boolean isStatic){
        IrFunction irf = new IrFunction(currentClass + "." + f.getName(), currentClass, f.getName(), isStatic);
        LoweringContext ctx = new LoweringContext();
        if(!isStatic){
            ctx.allocSlot("this");
        }
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
            String label = ws.getLabel();
            out.instructions.add(new IrLabel(loopL));

            // For desugared for-loops, place a continue label before the increment
            // so that 'continue' executes the increment before re-checking the condition.
            Statement body = ws.getBody();
            boolean isForLoop = (body instanceof Block blk && blk.isDesugaredForLoopBody()
                    && blk.getStatements().size() >= 2);
            String continueL = loopL; // default: continue goes to condition
            if (isForLoop) {
                continueL = freshLabel("forcont");
            }
            ctx.pushLoop(continueL, endL, label);
            int cond = lowerExpr(ws.getCondition(), out, ctx, currentClass);
            out.instructions.add(new IrJumpIfFalse(cond, endL));
            if (isForLoop) {
                Block blk = (Block) body;
                java.util.List<Statement> stmts = blk.getStatements();
                // Lower all statements except the last (increment)
                for (int si = 0; si < stmts.size() - 1; si++) {
                    lowerStmt(stmts.get(si), out, ctx, currentClass);
                }
                // Place continue label here so continue jumps to increment
                out.instructions.add(new IrLabel(continueL));
                // Lower the increment
                lowerStmt(stmts.get(stmts.size() - 1), out, ctx, currentClass);
            } else {
                lowerStmt(body, out, ctx, currentClass);
            }
            // Pop after body lowering
            ctx.popLoop(label);
            out.instructions.add(new IrJump(loopL));
            out.instructions.add(new IrLabel(endL));
        } else if(s instanceof Block blk){
            for(Statement st: blk.getStatements()){
                lowerStmt(st, out, ctx, currentClass);
            }
        } else if(s instanceof BreakStmt bs){
            String br = bs.getLabel() != null ? ctx.breakForLabel(bs.getLabel()) : ctx.currentBreak();
            if(br != null){ out.instructions.add(new IrJump(br)); }
        } else if(s instanceof ContinueStmt cs){
            String cont = cs.getLabel() != null ? ctx.continueForLabel(cs.getLabel()) : ctx.currentContinue();
            if(cont != null){ out.instructions.add(new IrJump(cont)); }
        } else if(s instanceof TryStmt ts){
            boolean enteredNestedTryWithinCatch = false;
            if(ctx.isInCatchBody()){
                ctx.enterNestedTryWithinCatch();
                enteredNestedTryWithinCatch = true;
            }
            String endL = freshLabel("try_end");
            int catchCount = (ts.getCatchClauses()==null)? 0 : ts.getCatchClauses().size();
            String[] catchLabels = new String[catchCount];
            boolean hasFinally = ts.getFinallyBlock() != null;
            // Pre-create labels for each catch
            for(int i=0;i<catchCount;i++) catchLabels[i] = freshLabel("catch"+i);
            // Push handlers for each catch in reverse order so first clause has highest priority
            for(int i=catchCount-1;i>=0;i--){
                CatchClause cc = ts.getCatchClauses().get(i);
                String ctype = cc.getExceptionType()!=null? cc.getExceptionType() : "any";
                out.instructions.add(new IrTryPush(catchLabels[i], ctype));
            }
            // Track finally scope for returns (and for throws only when there are no catches).
            // When catches exist, throw inside the try body is intended to transfer to a catch clause
            // before finally runs.
            if(hasFinally){
                ctx.pushFinally(ts.getFinallyBlock(), catchCount == 0, false);
            }

            // Lower try block
            lowerStmt(ts.getTryBlock(), out, ctx, currentClass);

            if(hasFinally){
                ctx.popFinally();
            }
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
                // Lower catch body with finally scope that applies on throws/returns.
                if(hasFinally){
                    ctx.enterCatchBody();
                    ctx.pushFinally(ts.getFinallyBlock(), true, true);
                }
                lowerStmt(cc.getBody(), out, ctx, currentClass);
                if(hasFinally){
                    ctx.popFinally();
                    ctx.exitCatchBody();
                }
                // finally after catch
                if(ts.getFinallyBlock()!=null){ lowerStmt(ts.getFinallyBlock(), out, ctx, currentClass); }
                out.instructions.add(new IrJump(endL));
            }
            out.instructions.add(new IrLabel(endL));

            if(enteredNestedTryWithinCatch){
                ctx.exitNestedTryWithinCatch();
            }
        } else if(s instanceof ThrowStmt th){
            emitFinallyBlocks(ctx, out, currentClass, true);
            int v = lowerExpr(th.getValue(), out, ctx, currentClass);
            out.instructions.add(new IrThrow(v));
        } else if(s instanceof ReturnStmt rs){
            emitFinallyBlocks(ctx, out, currentClass, false);
            Expression value = rs.getValue();
            if(value == null){
                out.instructions.add(new IrReturn(null));
            } else {
                int v = lowerExpr(value, out, ctx, currentClass);
                out.instructions.add(new IrReturn(v));
            }
        } else {
            errorReporter.error(ErrorFactory.getLocation(s),
                    "IR backend does not support statement: " + (s==null?"<null>":s.getClass().getSimpleName()),
                    "Run without --backend=ir, or refactor the program to avoid this construct.");
        }
    }

    private void emitFinallyBlocks(LoweringContext ctx, IrFunction out, String currentClass, boolean forThrow){
        for(LoweringContext.FinallyScope scope : ctx.finallyScopes()){
            if(forThrow && !scope.applyOnThrow) continue;
            if(forThrow && scope.fromCatchBody && ctx.isInsideNestedTryWithinCatch()) continue;
            lowerStmt(scope.finallyBlock, out, ctx, currentClass);
        }
    }

    private int lowerExpr(Expression e, IrFunction out, LoweringContext ctx, String currentClass){
        if(e instanceof ThisExpr){
            int thisSlot = ctx.getSlot("this");
            int t = ctx.newTemp();
            if(thisSlot >= 0) out.instructions.add(new IrLoadLocal(thisSlot, t));
            else out.instructions.add(new IrConst(t, null));
            return t;
        }
        if(e instanceof SuperExpr se){
            // Standalone super.method (outside of a call) — not supported as a value
            errorReporter.error(ErrorFactory.getLocation(se),
                    "IR backend does not support 'super' as a standalone expression. Use super.method(...) as a call.",
                    "Call the superclass method directly, e.g. super.method(args).");
            int t = ctx.newTemp();
            out.instructions.add(new IrConst(t, null));
            return t;
        }
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
        } else if(e instanceof TernaryExpr te){
            int cond = lowerExpr(te.getCondition(), out, ctx, currentClass);
            String elseL = freshLabel("ternary_else");
            String endL = freshLabel("ternary_end");
            int result = ctx.newTemp();
            out.instructions.add(new IrJumpIfFalse(cond, elseL));
            int thenVal = lowerExpr(te.getThenBranch(), out, ctx, currentClass);
            out.instructions.add(new IrStoreLocal(thenVal, result));
            out.instructions.add(new IrJump(endL));
            out.instructions.add(new IrLabel(elseL));
            int elseVal = lowerExpr(te.getElseBranch(), out, ctx, currentClass);
            out.instructions.add(new IrStoreLocal(elseVal, result));
            out.instructions.add(new IrLabel(endL));
            return result;
        } else if(e instanceof ArrayExpr arr){
            // Lower array literal by allocating and storing each element
            java.util.List<Expression> elems = arr.getElements();
            int size = ctx.newTemp(); out.instructions.add(new IrConst(size, (long) elems.size()));
            int arrSlot = ctx.newTemp(); out.instructions.add(new IrNewArray(size, arrSlot, null));
            for(int i=0;i<elems.size();i++){
                int idx = ctx.newTemp(); out.instructions.add(new IrConst(idx, (long) i));
                int val = lowerExpr(elems.get(i), out, ctx, currentClass);
                out.instructions.add(new IrStoreElement(arrSlot, idx, val));
            }
            return arrSlot;
        } else if(e instanceof NewArrayExpr na){
            java.util.List<Expression> sizes = na.getSizes();
            if(sizes == null || sizes.isEmpty()){
                Expression singleSize = na.getSize();
                int sz = lowerExpr(singleSize, out, ctx, currentClass);
                int arrSlot = ctx.newTemp();
                out.instructions.add(new IrNewArray(sz, arrSlot, na.getElementType()));
                return arrSlot;
            }

            java.util.List<Integer> sizeSlots = new java.util.ArrayList<>();
            boolean jagged = false;
            for(Expression sizeExpr : sizes){
                if(sizeExpr == null){
                    jagged = true;
                    break;
                }
                sizeSlots.add(lowerExpr(sizeExpr, out, ctx, currentClass));
            }
            if(sizeSlots.isEmpty()){
                errorReporter.error(ErrorFactory.getLocation(na),
                        "IR backend requires at least one array dimension size.",
                        "Provide an explicit size for the first array dimension.");
                int t = ctx.newTemp();
                out.instructions.add(new IrConst(t, null));
                return t;
            }

            boolean rootIsLeaf = sizeSlots.size() == 1 && !jagged;
            int arrSlot = ctx.newTemp();
            out.instructions.add(new IrNewArray(sizeSlots.get(0), arrSlot, rootIsLeaf ? na.getElementType() : null));
            if(sizeSlots.size() > 1){
                emitNestedArrayPopulation(out, ctx, currentClass, arrSlot, sizeSlots, 0, na.getElementType(), jagged);
            }
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
            // super.method(args...) — non-virtual call to superclass method
            if(callee instanceof SuperExpr se){
                String methodName = se.method.getLexeme();
                ClassDecl cd = classIndex.get(currentClass);
                if(cd == null || cd.getSuperclass() == null){
                    errorReporter.error(ErrorFactory.getLocation(se),
                            "Cannot use 'super' in a class with no superclass.",
                            "Ensure the current class extends another class.");
                    int t = ctx.newTemp(); out.instructions.add(new IrConst(t, null)); return t;
                }
                String superName = cd.getSuperclass().getName().getLexeme();
                String qn = superName + "." + methodName;
                // Pass 'this' as first arg, then call args
                int thisSlot = ctx.getSlot("this");
                int thisTemp = ctx.newTemp();
                if(thisSlot >= 0) out.instructions.add(new IrLoadLocal(thisSlot, thisTemp));
                else out.instructions.add(new IrConst(thisTemp, null));
                int[] argSlots = new int[args.size() + 1];
                argSlots[0] = thisTemp;
                for(int i = 0; i < args.size(); i++) argSlots[i + 1] = lowerExpr(args.get(i), out, ctx, currentClass);
                int dest = ctx.newTemp();
                out.instructions.add(new IrCall(qn, argSlots, dest));
                return dest;
            }
            if(callee instanceof GetExpr ge){
                int obj = lowerExpr(ge.getObject(), out, ctx, currentClass);
                String methodName = ge.getName().getLexeme();
                if(isNativeStringMethod(methodName)){
                    int[] argSlots = new int[args.size() + 1];
                    argSlots[0] = obj;
                    for(int i=0;i<args.size();i++) argSlots[i + 1] = lowerExpr(args.get(i), out, ctx, currentClass);
                    int dest = ctx.newTemp();
                    out.instructions.add(new IrCallNative(methodName, argSlots, dest));
                    return dest;
                }
                int[] argSlots = new int[args.size()];
                for(int i=0;i<args.size();i++) argSlots[i] = lowerExpr(args.get(i), out, ctx, currentClass);
                int dest = ctx.newTemp();
                out.instructions.add(new IrInvokeMethod(obj, methodName, argSlots, dest));
                return dest;
            }
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
                if(dhrlang.stdlib.NativeSignatures.exists(name)){
                    int[] argSlots = new int[args.size()];
                    for(int i=0;i<args.size();i++) argSlots[i] = lowerExpr(args.get(i), out, ctx, currentClass);
                    int dest = ctx.newTemp();
                    out.instructions.add(new IrCallNative(name, argSlots, dest));
                    return dest;
                }
                // Attempt to lower a user-defined function call in the same class (up to 4 args)
                String qn = name.contains(".")? name : (currentClass + "." + name);
                int argc = args.size();
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
                int argc = args.size();
                int[] argSlots = new int[argc];
                for(int i=0;i<argc;i++) argSlots[i] = lowerExpr(args.get(i), out, ctx, currentClass);
                int dest = ctx.newTemp();
                out.instructions.add(new IrCall(qn, argSlots, dest));
                return dest;
            }
            errorReporter.error(ErrorFactory.getLocation(ce),
                    "IR backend does not support this kind of call target.",
                    "Only simple function calls (name(...) or ClassName.method(...)) are currently supported.");
            int t = ctx.newTemp(); out.instructions.add(new IrConst(t, null)); return t;
        } else if(e instanceof NewExpr ne){
            String className = baseClassName(ne.getClassName());
            int obj = ctx.newTemp();
            out.instructions.add(new IrNewObject(className, obj));
            boolean hasInitializer = findMethodInHierarchy(className, "init") != null;
            if(!ne.getArguments().isEmpty() || hasInitializer){
                if(!hasInitializer && !ne.getArguments().isEmpty()){
                    errorReporter.error(ErrorFactory.getLocation(ne),
                            "IR backend could not find constructor 'init' for class '" + className + "'.",
                            "Add an init(...) method to the class, or instantiate it with no arguments.");
                    return obj;
                }
                int[] argSlots = new int[ne.getArguments().size()];
                for(int i=0;i<ne.getArguments().size();i++) argSlots[i] = lowerExpr(ne.getArguments().get(i), out, ctx, currentClass);
                out.instructions.add(new IrInvokeMethod(obj, "init", argSlots, -1));
            }
            return obj;
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
        } else if(e instanceof PrefixIncrementExpr pie){
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
                // Prefix returns new value
                return newVal;
            } else {
                int t = ctx.newTemp();
                out.instructions.add(new IrConst(t, null));
                return t;
            }
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
                    case "%" -> out.instructions.add(new IrBinOp(IrBinOp.Op.MOD, l, r, t));
                    case "&" -> out.instructions.add(new IrBinOp(IrBinOp.Op.BIT_AND, l, r, t));
                    case "|" -> out.instructions.add(new IrBinOp(IrBinOp.Op.BIT_OR, l, r, t));
                    case "^" -> out.instructions.add(new IrBinOp(IrBinOp.Op.BIT_XOR, l, r, t));
                    case "<<" -> out.instructions.add(new IrBinOp(IrBinOp.Op.LSHIFT, l, r, t));
                    case ">>" -> out.instructions.add(new IrBinOp(IrBinOp.Op.RSHIFT, l, r, t));
                    case "==" -> out.instructions.add(new IrCompare(IrCompare.Op.EQ, l, r, t));
                    case "!=" -> out.instructions.add(new IrCompare(IrCompare.Op.NEQ, l, r, t));
                    case "<" -> out.instructions.add(new IrCompare(IrCompare.Op.LT, l, r, t));
                    case "<=" -> out.instructions.add(new IrCompare(IrCompare.Op.LE, l, r, t));
                    case ">" -> out.instructions.add(new IrCompare(IrCompare.Op.GT, l, r, t));
                    case ">=" -> out.instructions.add(new IrCompare(IrCompare.Op.GE, l, r, t));
                    default -> {
                        errorReporter.error(ErrorFactory.getLocation(be),
                                "IR backend does not support binary operator '"+op+"'.",
                                "Run without --backend=ir, or avoid this operator.");
                        out.instructions.add(new IrConst(t, null));
                    }
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
                case "~" -> out.instructions.add(new IrUnaryOp(IrUnaryOp.Op.BIT_NOT, inner, t));
                default -> {
                    errorReporter.error(ErrorFactory.getLocation(ue),
                            "IR backend does not support unary operator '"+op+"'.",
                            "Run without --backend=ir, or avoid this operator.");
                    out.instructions.add(new IrConst(t, null));
                }
            }
            return t;
        } else if(e instanceof VariableExpr ve){
            String name = ve.getName()!=null? ve.getName().getLexeme(): "";
            int slot = ctx.getSlot(name);
            int t = ctx.newTemp();
            if(slot>=0) out.instructions.add(new IrLoadLocal(slot, t));
            else out.instructions.add(new IrConst(t, null));
            return t;
        }
        errorReporter.error(ErrorFactory.getLocation(e),
                "IR backend does not support expression: " + (e==null?"<null>":e.getClass().getSimpleName()),
                "Run without --backend=ir, or refactor the program to avoid this construct.");
        int t = ctx.newTemp();
        out.instructions.add(new IrConst(t, null));
        return t;
    }

    private Object literalValue(LiteralExpr le){
        return le.getValue();
    }

    private String baseClassName(String className){
        if(className == null) return "";
        int genericStart = className.indexOf('<');
        return genericStart >= 0 ? className.substring(0, genericStart) : className;
    }

    private void emitNestedArrayPopulation(IrFunction out, LoweringContext ctx, String currentClass,
                                           int parentArraySlot, java.util.List<Integer> sizeSlots,
                                           int depth, String elementType, boolean jagged){
        if(depth >= sizeSlots.size() - 1) return;

        int indexSlot = ctx.allocSlot(freshLabel("arr_idx_slot"));
        int zero = ctx.newTemp();
        out.instructions.add(new IrConst(zero, 0L));
        out.instructions.add(new IrStoreLocal(zero, indexSlot));

        String loopLabel = freshLabel("arr_loop");
        String endLabel = freshLabel("arr_loop_end");
        out.instructions.add(new IrLabel(loopLabel));

        int currentIndex = ctx.newTemp();
        out.instructions.add(new IrLoadLocal(indexSlot, currentIndex));
        int cond = ctx.newTemp();
        out.instructions.add(new IrCompare(IrCompare.Op.LT, currentIndex, sizeSlots.get(depth), cond));
        out.instructions.add(new IrJumpIfFalse(cond, endLabel));

        boolean childIsLeaf = depth + 1 == sizeSlots.size() - 1 && !jagged;
        int childArray = ctx.newTemp();
        out.instructions.add(new IrNewArray(sizeSlots.get(depth + 1), childArray, childIsLeaf ? elementType : null));
        out.instructions.add(new IrStoreElement(parentArraySlot, currentIndex, childArray));
        emitNestedArrayPopulation(out, ctx, currentClass, childArray, sizeSlots, depth + 1, elementType, jagged);

        int one = ctx.newTemp();
        out.instructions.add(new IrConst(one, 1L));
        int nextIndex = ctx.newTemp();
        out.instructions.add(new IrBinOp(IrBinOp.Op.ADD, currentIndex, one, nextIndex));
        out.instructions.add(new IrStoreLocal(nextIndex, indexSlot));
        out.instructions.add(new IrJump(loopLabel));
        out.instructions.add(new IrLabel(endLabel));
    }

    private boolean isNativeStringMethod(String methodName){
        return switch(methodName){
            case "length", "substring", "charAt", "toUpperCase", "toLowerCase", "indexOf", "replace",
                    "startsWith", "endsWith", "trim", "split", "join", "repeat", "reverse", "padLeft",
                    "padRight" -> true;
            default -> false;
        };
    }

    private FunctionDecl findMethodInHierarchy(String className, String methodName){
        ClassDecl cd = classIndex.get(className);
        if(cd == null) return null;
        FunctionDecl local = cd.findMethod(methodName);
        if(local != null) return local;
        if(cd.getSuperclass() != null){
            return findMethodInHierarchy(cd.getSuperclass().getName().getLexeme(), methodName);
        }
        return null;
    }

    private int labelCounter = 0;
    private String freshLabel(String prefix){ return prefix+"_"+(labelCounter++); }
}
