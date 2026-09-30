package dhrlang.bytecode;

import dhrlang.error.SourceLocation;
import dhrlang.ir.IrInstruction;
import dhrlang.ir.IrLabel;
import dhrlang.ir.IrProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A bounded instruction prefix with source provenance, never local values or timestamps. */
public final class ExecutionTrace implements BytecodeVM.InstructionObserver {
    public static final int MAX_STEPS = 128;

    public record Step(int sequence, int functionIndex, String function, int instruction, String opcode, int depth,
                       String file, int line, int column) {}
    public record Snapshot(List<Step> steps, boolean truncated) {
        public Snapshot {
            if (steps == null || steps.size() > MAX_STEPS) throw new IllegalArgumentException("Invalid trace size");
            steps = List.copyOf(steps);
        }
    }

    private final List<List<SourceLocation>> locations = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private boolean truncated;

    public ExecutionTrace(IrProgram program, Map<IrInstruction, SourceLocation> sourceLocations) {
        for (var function : program.functions) {
            // BytecodeWriter emits exactly one instruction for each non-label IR instruction.
            locations.add(function.instructions.stream()
                    .filter(instruction -> !(instruction instanceof IrLabel)).map(sourceLocations::get).toList());
        }
    }

    @Override
    public void beforeInstruction(int functionIndex, String function, int instruction, BytecodeOpcode opcode, int depth) {
        if (steps.size() == MAX_STEPS) {
            truncated = true;
            return;
        }
        if (functionIndex < 0 || functionIndex >= locations.size()) {
            throw new IllegalArgumentException("Execution trace has no matching function provenance");
        }
        List<SourceLocation> functionLocations = locations.get(functionIndex);
        if (instruction < 0 || instruction >= functionLocations.size()) {
            throw new IllegalArgumentException("Execution trace has no matching instruction provenance");
        }
        SourceLocation location = functionLocations.get(instruction);
        steps.add(new Step(steps.size() + 1, functionIndex, bounded(function), instruction, opcode.name(), depth,
                location == null ? "" : bounded(location.getFilename()),
                location == null ? 0 : location.getLine(), location == null ? 0 : location.getColumn()));
    }

    public Snapshot snapshot() {
        return new Snapshot(steps, truncated);
    }

    public static Snapshot empty() {
        return new Snapshot(List.of(), false);
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
