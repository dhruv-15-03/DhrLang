package dhrlang.ir;

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

    int allocSlot(String name){ return localSlots.computeIfAbsent(name, k-> nextSlot++); }
    int getSlot(String name){ return localSlots.getOrDefault(name,-1); }
    int newTemp(){ return nextSlot++; }

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
