package dhrlang.ir;

import dhrlang.ast.Statement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Tracks locals -> slot mapping and loop labels while lowering a single function. */
class LoweringContext {
    private int nextSlot = 0;
    private final Map<String,Integer> localSlots = new HashMap<>();
    private final Deque<String> continueLabels = new ArrayDeque<>();
    private final Deque<String> breakLabels = new ArrayDeque<>();

    static final class FinallyScope {
        final Statement finallyBlock;
        final boolean applyOnThrow;
        final boolean fromCatchBody;
        FinallyScope(Statement finallyBlock, boolean applyOnThrow, boolean fromCatchBody){
            this.finallyBlock = finallyBlock;
            this.applyOnThrow = applyOnThrow;
            this.fromCatchBody = fromCatchBody;
        }
    }

    private final Deque<FinallyScope> finallyScopes = new ArrayDeque<>();

    // Track whether we are lowering inside a catch body, and whether we are inside a nested try
    // statement within that catch body. This lets us avoid emitting a catch-scope finally for throws
    // that are actually caught by the nested try.
    private int catchBodyDepth = 0;
    private int nestedTryWithinCatchDepth = 0;

    int allocSlot(String name){ return localSlots.computeIfAbsent(name, k-> nextSlot++); }
    int getSlot(String name){ return localSlots.getOrDefault(name,-1); }
    int newTemp(){ return nextSlot++; }

    void pushFinally(Statement finallyBlock, boolean applyOnThrow, boolean fromCatchBody){
        if(finallyBlock != null) finallyScopes.push(new FinallyScope(finallyBlock, applyOnThrow, fromCatchBody));
    }
    void popFinally(){
        if(!finallyScopes.isEmpty()) finallyScopes.pop();
    }
    Iterable<FinallyScope> finallyScopes(){
        return finallyScopes;
    }

    void enterCatchBody(){ catchBodyDepth++; }
    void exitCatchBody(){ if(catchBodyDepth>0) catchBodyDepth--; }
    boolean isInCatchBody(){ return catchBodyDepth>0; }

    void enterNestedTryWithinCatch(){ if(isInCatchBody()) nestedTryWithinCatchDepth++; }
    void exitNestedTryWithinCatch(){ if(nestedTryWithinCatchDepth>0) nestedTryWithinCatchDepth--; }
    boolean isInsideNestedTryWithinCatch(){ return nestedTryWithinCatchDepth>0; }

    void pushLoop(String continueLabel, String breakLabel){
        continueLabels.push(continueLabel);
        breakLabels.push(breakLabel);
    }
    void popLoop(){
        if(!continueLabels.isEmpty()) continueLabels.pop();
        if(!breakLabels.isEmpty()) breakLabels.pop();
    }
    String currentContinue(){ return continueLabels.peek(); }
    String currentBreak(){ return breakLabels.peek(); }
}
