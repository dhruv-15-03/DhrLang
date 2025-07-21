package dhrlang.interpreter;

import dhrlang.error.SourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the execution stack for better error reporting
 */
public class ExecutionStack {
    private final List<StackFrame> frames = new ArrayList<>();
    
    public void push(String functionName, String className, SourceLocation location) {
        frames.add(new StackFrame(functionName, className, location));
    }
    
    public void pop() {
        if (!frames.isEmpty()) {
            frames.remove(frames.size() - 1);
        }
    }
    
    public List<StackFrame> getFrames() {
        return new ArrayList<>(frames);
    }
    
    public String getStackTrace() {
        if (frames.isEmpty()) {
            return "  (no stack trace available)";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = frames.size() - 1; i >= 0; i--) {
            sb.append("  at ").append(frames.get(i)).append("\n");
        }
        return sb.toString();
    }
    
    public StackFrame getCurrentFrame() {
        return frames.isEmpty() ? null : frames.get(frames.size() - 1);
    }
}
