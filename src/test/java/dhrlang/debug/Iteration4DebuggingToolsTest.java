package dhrlang.debug;

import dhrlang.ast.*;
import dhrlang.evm.EvmCodeBuffer;
import dhrlang.evm.EvmOpcode;
import dhrlang.interpreter.*;
import dhrlang.validation.StorageLayouter;
import dhrlang.validation.StorageLayouter.ContractLayout;
import dhrlang.validation.StorageLayouter.SlotInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 4 – Debugging &amp; Tooling
 *
 * <p>Tests cover all new classes in the {@code dhrlang.debug} package:
 * InspectFunction, TraceRecorder, GasProfiler, StorageLayoutVisualizer,
 * CallGraphGenerator, and ContractDebugger.</p>
 */
class Iteration4DebuggingToolsTest {

    // ═══════════════════════════════════════════════════
    //  Helper: build a simple ContractLayout for testing
    // ═══════════════════════════════════════════════════

    private static ContractLayout makeLayout(String name, String... fields) {
        // fields: name1, type1, size1, name2, type2, size2, ...
        List<SlotInfo> slots = new ArrayList<>();
        for (int i = 0; i + 2 < fields.length; i += 3) {
            slots.add(new SlotInfo(fields[i], fields[i + 1], slots.size(),
                    Integer.parseInt(fields[i + 2])));
        }
        return new ContractLayout(name, slots);
    }

    /** Build minimal FunctionDecl with no body. */
    private static FunctionDecl makeFn(String name) {
        return new FunctionDecl("void", name, List.of(), new Block(List.of()));
    }

    /** Build FunctionDecl with statements in body. */
    private static FunctionDecl makeFnWithBody(String name, List<Statement> body) {
        return new FunctionDecl("void", name, List.of(), new Block(body));
    }

    /** Build a simple ClassDecl with functions. */
    private static ClassDecl makeClass(String name, FunctionDecl... fns) {
        return new ClassDecl(name, null, List.of(fns), List.of());
    }

    // ═══════════════════════════════════════════════════
    //  1.  InspectFunction
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("InspectFunction")
    class InspectFunctionTests {

        @Test @DisplayName("inspect null returns kya type")
        void inspectNull() {
            String result = InspectFunction.formatValue(null);
            assertTrue(result.contains("null"), "should mention null");
            assertTrue(result.contains("kya"), "should mention kya type");
        }

        @Test @DisplayName("inspect long value")
        void inspectLong() {
            String result = InspectFunction.formatValue(42L);
            assertTrue(result.contains("num"), "should identify as num");
            assertTrue(result.contains("42"), "should contain value");
            assertTrue(result.contains("byte"), "42 fits in byte range");
        }

        @Test @DisplayName("inspect large long value")
        void inspectLargeLong() {
            String result = InspectFunction.formatValue(Long.MAX_VALUE);
            assertTrue(result.contains("long"), "large value in long range");
        }

        @Test @DisplayName("inspect double value")
        void inspectDouble() {
            String result = InspectFunction.formatValue(3.14);
            assertTrue(result.contains("duo"), "should identify as duo");
            assertTrue(result.contains("3.14"), "should contain value");
        }

        @Test @DisplayName("inspect NaN double")
        void inspectNaN() {
            String result = InspectFunction.formatValue(Double.NaN);
            assertTrue(result.contains("NaN"), "should flag NaN");
        }

        @Test @DisplayName("inspect boolean value")
        void inspectBoolean() {
            String result = InspectFunction.formatValue(true);
            assertTrue(result.contains("sab"), "should identify as sab");
            assertTrue(result.contains("true"), "should contain value");
        }

        @Test @DisplayName("inspect string with length")
        void inspectString() {
            String result = InspectFunction.formatValue("hello");
            assertTrue(result.contains("string"), "should identify as string");
            assertTrue(result.contains("hello"), "should contain value");
            assertTrue(result.contains("5"), "should show length");
        }

        @Test @DisplayName("inspect empty array")
        void inspectEmptyArray() {
            String result = InspectFunction.formatValue(new Object[0]);
            assertTrue(result.contains("array"), "should identify as array");
            assertTrue(result.contains("0"), "should show length 0");
        }

        @Test @DisplayName("inspect array with elements")
        void inspectArrayWithElements() {
            Object[] arr = new Object[]{1L, 2L, 3L};
            String result = InspectFunction.formatValue(arr);
            assertTrue(result.contains("array"), "should identify as array");
            assertTrue(result.contains("3"), "should show length");
        }

        @Test @DisplayName("getTypeName returns correct types")
        void getTypeName() {
            assertEquals("kya", InspectFunction.getTypeName(null));
            assertEquals("num", InspectFunction.getTypeName(42L));
            assertEquals("duo", InspectFunction.getTypeName(3.14));
            assertEquals("sab", InspectFunction.getTypeName(true));
            assertEquals("string", InspectFunction.getTypeName("test"));
            assertEquals("array", InspectFunction.getTypeName(new Object[0]));
        }

        @Test @DisplayName("getObjectDetails returns map with type and value")
        void getObjectDetails() {
            Map<String, String> details = InspectFunction.getObjectDetails(42L);
            assertEquals("num", details.get("type"));
            assertEquals("42", details.get("value"));
            assertEquals("byte", details.get("range"));
        }

        @Test @DisplayName("formatFields on non-instance returns message")
        void formatFieldsNonInstance() {
            String result = InspectFunction.formatFields("hello");
            assertTrue(result.contains("not an instance"), "should say not an instance");
        }

        @Test @DisplayName("inspect NativeFunction returns callable info")
        void inspectNativeFunction() {
            NativeFunction fn = new NativeFunction() {
                @Override public int arity() { return 2; }
                @Override public Object call(Interpreter i, List<Object> a) { return null; }
            };
            String result = InspectFunction.formatValue(fn);
            assertTrue(result.contains("callable"), "should identify as callable");
            assertTrue(result.contains("2"), "should show arity");
        }

        @Test @DisplayName("inspect factory method creates callable NativeFunction")
        void inspectFactory() {
            NativeFunction inspectFn = InspectFunction.inspect();
            assertEquals(1, inspectFn.arity());
            // Can call with a Long arg
            Object result = inspectFn.call(null, List.of(99L));
            assertTrue(result instanceof String);
            assertTrue(((String) result).contains("num"));
        }

        @Test @DisplayName("inspectType factory returns type name")
        void inspectTypeFactory() {
            NativeFunction fn = InspectFunction.inspectType();
            assertEquals(1, fn.arity());
            assertEquals("string", fn.call(null, List.of("abc")));
        }

        @Test @DisplayName("formatStack with no interpreter")
        void formatStackNoInterpreter() {
            String result = InspectFunction.formatStack(null);
            assertTrue(result.contains("no interpreter"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  2.  TraceRecorder
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("TraceRecorder")
    class TraceRecorderTests {

        private TraceRecorder recorder;

        @BeforeEach
        void init() {
            recorder = new TraceRecorder();
        }

        @Test @DisplayName("initially disabled")
        void initiallyDisabled() {
            assertFalse(recorder.isEnabled());
            assertEquals(0, recorder.getEntryCount());
        }

        @Test @DisplayName("discards events when disabled")
        void discardsWhenDisabled() {
            recorder.recordCall("foo", null, List.of());
            assertEquals(0, recorder.getEntryCount());
        }

        @Test @DisplayName("records events when enabled")
        void recordsWhenEnabled() {
            recorder.enable();
            recorder.recordCall("foo", "Bar", List.of(1L, 2L));
            assertEquals(1, recorder.getEntryCount());
        }

        @Test @DisplayName("records call and return pair")
        void callAndReturn() {
            recorder.enable();
            recorder.recordCall("transfer", "Token", List.of("addr", 100L));
            recorder.recordReturn("transfer", true);
            assertEquals(2, recorder.getEntryCount());

            List<TraceRecorder.TraceEntry> entries = recorder.getEntries();
            assertEquals(TraceRecorder.EntryType.CALL, entries.get(0).getType());
            assertEquals(TraceRecorder.EntryType.RETURN, entries.get(1).getType());
        }

        @Test @DisplayName("tracks max depth")
        void tracksMaxDepth() {
            recorder.enable();
            recorder.recordCall("a", null, List.of());
            recorder.recordCall("b", null, List.of());
            recorder.recordCall("c", null, List.of());
            assertEquals(3, recorder.getMaxDepth());
            recorder.recordReturn("c", null);
            recorder.recordReturn("b", null);
            assertEquals(3, recorder.getMaxDepth()); // max stays
        }

        @Test @DisplayName("records state changes")
        void stateChanges() {
            recorder.enable();
            recorder.recordStateChange("balance", 100L, 80L);
            List<TraceRecorder.TraceEntry> stateEntries =
                    recorder.getEntriesOfType(TraceRecorder.EntryType.STATE_CHANGE);
            assertEquals(1, stateEntries.size());
            assertTrue(stateEntries.get(0).getDetail().contains("100"));
            assertTrue(stateEntries.get(0).getDetail().contains("80"));
        }

        @Test @DisplayName("records branch decisions")
        void branchDecisions() {
            recorder.enable();
            recorder.recordBranch("x > 0", true);
            recorder.recordBranch("y == null", false);
            List<TraceRecorder.TraceEntry> branches =
                    recorder.getEntriesOfType(TraceRecorder.EntryType.BRANCH);
            assertEquals(2, branches.size());
            assertTrue(branches.get(0).getDetail().contains("taken"));
            assertTrue(branches.get(1).getDetail().contains("not-taken"));
        }

        @Test @DisplayName("records exceptions")
        void exceptions() {
            recorder.enable();
            recorder.recordException("TypeError", "Cannot add string to num");
            assertEquals(1, recorder.getEntriesOfType(TraceRecorder.EntryType.EXCEPTION).size());
        }

        @Test @DisplayName("records log events")
        void logEvents() {
            recorder.enable();
            recorder.recordLogEvent("Transfer", "from=0x1, to=0x2, amount=100");
            assertEquals(1, recorder.getEntriesOfType(TraceRecorder.EntryType.LOG_EVENT).size());
        }

        @Test @DisplayName("getEntryCounts returns all types")
        void entryCounts() {
            recorder.enable();
            recorder.recordCall("a", null, List.of());
            recorder.recordReturn("a", null);
            recorder.recordStateChange("x", 1L, 2L);

            Map<TraceRecorder.EntryType, Integer> counts = recorder.getEntryCounts();
            assertEquals(1, counts.get(TraceRecorder.EntryType.CALL));
            assertEquals(1, counts.get(TraceRecorder.EntryType.RETURN));
            assertEquals(1, counts.get(TraceRecorder.EntryType.STATE_CHANGE));
            assertEquals(0, counts.get(TraceRecorder.EntryType.BRANCH));
        }

        @Test @DisplayName("formatTrace produces readable output")
        void formatTrace() {
            recorder.enable();
            recorder.recordCall("init", "Token", List.of());
            recorder.recordReturn("init", null);
            String trace = recorder.formatTrace();
            assertTrue(trace.contains("Execution Trace"));
            assertTrue(trace.contains("CALL"));
            assertTrue(trace.contains("Token.init"));
        }

        @Test @DisplayName("formatTrace for empty recorder")
        void formatTraceEmpty() {
            String trace = recorder.formatTrace();
            assertTrue(trace.contains("empty"));
        }

        @Test @DisplayName("clear resets all state")
        void clearResets() {
            recorder.enable();
            recorder.recordCall("a", null, List.of());
            recorder.recordCall("b", null, List.of());
            recorder.clear();
            assertEquals(0, recorder.getEntryCount());
            assertEquals(0, recorder.getMaxDepth());
        }

        @Test @DisplayName("getLastEntries returns correct subset")
        void getLastEntries() {
            recorder.enable();
            for (int i = 0; i < 10; i++) {
                recorder.recordCall("fn" + i, null, List.of());
            }
            List<TraceRecorder.TraceEntry> last3 = recorder.getLastEntries(3);
            assertEquals(3, last3.size());
            assertTrue(last3.get(0).getLabel().contains("fn7"));
        }

        @Test @DisplayName("formatSummary shows counts")
        void formatSummary() {
            recorder.enable();
            recorder.recordCall("a", null, List.of());
            recorder.recordReturn("a", null);
            String summary = recorder.formatSummary();
            assertTrue(summary.contains("CALL: 1"));
            assertTrue(summary.contains("RETURN: 1"));
        }

        @Test @DisplayName("sequence numbers are monotonic")
        void sequenceNumbers() {
            recorder.enable();
            recorder.recordCall("a", null, List.of());
            recorder.recordReturn("a", null);
            List<TraceRecorder.TraceEntry> entries = recorder.getEntries();
            assertEquals(0, entries.get(0).getSequenceNumber());
            assertEquals(1, entries.get(1).getSequenceNumber());
        }
    }

    // ═══════════════════════════════════════════════════
    //  3.  GasProfiler
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("GasProfiler")
    class GasProfilerTests {

        private GasProfiler profiler;

        @BeforeEach
        void init() {
            profiler = new GasProfiler();
        }

        @Test @DisplayName("initially zero gas")
        void initiallyZero() {
            assertEquals(0, profiler.getTotalGas());
            assertEquals(0, profiler.getTotalOpcodes());
        }

        @Test @DisplayName("records single opcode gas")
        void singleOpcode() {
            profiler.recordOpcode(EvmOpcode.ADD);
            assertEquals(3, profiler.getTotalGas()); // ADD = 3 gas
            assertEquals(1, profiler.getTotalOpcodes());
        }

        @Test @DisplayName("accumulates gas across opcodes")
        void multipleOpcodes() {
            profiler.recordOpcode(EvmOpcode.ADD);    // 3
            profiler.recordOpcode(EvmOpcode.MUL);    // 5
            profiler.recordOpcode(EvmOpcode.SLOAD);  // 100
            assertEquals(108, profiler.getTotalGas());
            assertEquals(3, profiler.getTotalOpcodes());
        }

        @Test @DisplayName("per-function gas tracking")
        void perFunctionGas() {
            profiler.startFunction("transfer");
            profiler.recordOpcode(EvmOpcode.SLOAD);   // 100
            profiler.recordOpcode(EvmOpcode.ADD);      // 3
            profiler.recordOpcode(EvmOpcode.SSTORE);   // 100
            profiler.endFunction("transfer");

            profiler.startFunction("balance");
            profiler.recordOpcode(EvmOpcode.SLOAD);    // 100
            profiler.endFunction("balance");

            Map<String, Long> perFunc = profiler.getPerFunctionGas();
            assertEquals(203L, perFunc.get("transfer"));
            assertEquals(100L, perFunc.get("balance"));
        }

        @Test @DisplayName("per-opcode breakdown")
        void perOpcodeBreakdown() {
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.recordOpcode(EvmOpcode.MUL);

            Map<EvmOpcode, Long> perOp = profiler.getPerOpcodeGas();
            assertEquals(6L, perOp.get(EvmOpcode.ADD));   // 3 * 2
            assertEquals(5L, perOp.get(EvmOpcode.MUL));   // 5 * 1
        }

        @Test @DisplayName("per-opcode count tracking")
        void perOpcodeCount() {
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.recordOpcode(EvmOpcode.ADD);

            Map<EvmOpcode, Integer> counts = profiler.getPerOpcodeCount();
            assertEquals(3, counts.get(EvmOpcode.ADD));
        }

        @Test @DisplayName("hotspots sorted by gas descending")
        void hotspots() {
            profiler.startFunction("cheap");
            profiler.recordOpcode(EvmOpcode.ADD);     // 3
            profiler.endFunction("cheap");

            profiler.startFunction("expensive");
            profiler.recordOpcode(EvmOpcode.SSTORE);  // 100
            profiler.endFunction("expensive");

            List<GasProfiler.GasHotspot> hotspots = profiler.getHotspots(2);
            assertEquals(2, hotspots.size());
            assertEquals("expensive", hotspots.get(0).getFunctionName());
            assertEquals(100, hotspots.get(0).getGasUsed());
            assertTrue(hotspots.get(0).getPercentage() > 50);
        }

        @Test @DisplayName("getExpensiveOpcodes sorted by gas")
        void expensiveOpcodes() {
            profiler.recordOpcode(EvmOpcode.ADD);       // 3
            profiler.recordOpcode(EvmOpcode.SSTORE);    // 100
            profiler.recordOpcode(EvmOpcode.SLOAD);     // 100

            List<Map.Entry<EvmOpcode, Long>> expensive = profiler.getExpensiveOpcodes(2);
            assertEquals(2, expensive.size());
            // SSTORE and SLOAD both 100, either can be first
            assertTrue(expensive.get(0).getValue() >= expensive.get(1).getValue());
        }

        @Test @DisplayName("custom gas recording")
        void customGas() {
            profiler.startFunction("create");
            profiler.recordOpcodeWithGas(EvmOpcode.SSTORE, 20000); // cold store
            profiler.endFunction("create");

            assertEquals(20000, profiler.getTotalGas());
            assertEquals(20000L, profiler.getPerFunctionGas().get("create"));
        }

        @Test @DisplayName("nested function calls")
        void nestedFunctions() {
            profiler.startFunction("outer");
            profiler.recordOpcode(EvmOpcode.ADD); // 3 → outer
            profiler.startFunction("inner");
            profiler.recordOpcode(EvmOpcode.MUL); // 5 → inner (top of stack)
            profiler.endFunction("inner");
            profiler.recordOpcode(EvmOpcode.SUB); // 3 → outer
            profiler.endFunction("outer");

            // inner gets MUL; outer gets ADD + SUB
            assertEquals(5L, profiler.getPerFunctionGas().get("inner"));
            assertEquals(6L, profiler.getPerFunctionGas().get("outer"));
            assertEquals(11, profiler.getTotalGas());
        }

        @Test @DisplayName("formatReport produces readable output")
        void formatReport() {
            profiler.startFunction("test");
            profiler.recordOpcode(EvmOpcode.SLOAD);
            profiler.endFunction("test");
            String report = profiler.formatReport();
            assertTrue(report.contains("Gas Profile Report"));
            assertTrue(report.contains("test"));
            assertTrue(report.contains("SLOAD"));
        }

        @Test @DisplayName("formatCompact one-liner")
        void formatCompact() {
            profiler.recordOpcode(EvmOpcode.ADD);
            String compact = profiler.formatCompact();
            assertTrue(compact.contains("Gas:"));
            assertTrue(compact.contains("1 ops"));
        }

        @Test @DisplayName("reset clears everything")
        void resetClears() {
            profiler.startFunction("fn");
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.endFunction("fn");
            profiler.reset();
            assertEquals(0, profiler.getTotalGas());
            assertEquals(0, profiler.getTotalOpcodes());
            assertTrue(profiler.getAllProfiles().isEmpty());
        }

        @Test @DisplayName("FunctionProfile tracks opcode counts")
        void functionProfileOpcodeCount() {
            profiler.startFunction("fn");
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.recordOpcode(EvmOpcode.ADD);
            profiler.recordOpcode(EvmOpcode.MUL);
            profiler.endFunction("fn");

            GasProfiler.FunctionProfile fp = profiler.getFunctionProfile("fn");
            assertNotNull(fp);
            assertEquals(3, fp.getOpcodeCount());
            assertEquals(11, fp.getGasUsed()); // 3+3+5
            assertEquals(2, fp.getOpcodeCountMap().get(EvmOpcode.ADD));
        }
    }

    // ═══════════════════════════════════════════════════
    //  4.  StorageLayoutVisualizer
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("StorageLayoutVisualizer")
    class StorageLayoutVisualizerTests {

        @Test @DisplayName("visualize null layout")
        void visualizeNull() {
            assertEquals("(null layout)", StorageLayoutVisualizer.visualize(null));
        }

        @Test @DisplayName("visualize simple layout")
        void visualizeSimple() {
            ContractLayout layout = makeLayout("Token",
                    "owner", "Address", "20",
                    "totalSupply", "uint256", "32");

            String vis = StorageLayoutVisualizer.visualize(layout);
            assertTrue(vis.contains("Token"), "should contain contract name");
            assertTrue(vis.contains("owner"), "should contain field name");
            assertTrue(vis.contains("Address"), "should contain type");
            assertTrue(vis.contains("20 bytes"), "should contain size");
            assertTrue(vis.contains("totalSupply"), "should contain second field");
            assertTrue(vis.contains("Total: 2 slots"), "should show slot count");
        }

        @Test @DisplayName("visualize empty layout")
        void visualizeEmpty() {
            ContractLayout layout = makeLayout("Empty");
            String vis = StorageLayoutVisualizer.visualize(layout);
            assertTrue(vis.contains("no storage fields"));
            assertTrue(vis.contains("Total: 0 slots"));
        }

        @Test @DisplayName("visualizeCompact format")
        void visualizeCompact() {
            ContractLayout layout = makeLayout("Token",
                    "owner", "Address", "20");
            String compact = StorageLayoutVisualizer.visualizeCompact(layout);
            assertTrue(compact.contains("Token Storage (1 slots)"));
            assertTrue(compact.contains("[0] Address owner"));
        }

        @Test @DisplayName("visualizeCompact null layout")
        void visualizeCompactNull() {
            assertEquals("(null layout)", StorageLayoutVisualizer.visualizeCompact(null));
        }

        @Test @DisplayName("visualizeMemoryMap shows bars")
        void visualizeMemoryMap() {
            ContractLayout layout = makeLayout("Token",
                    "owner", "Address", "20",
                    "supply", "uint256", "32");
            String map = StorageLayoutVisualizer.visualizeMemoryMap(layout);
            assertTrue(map.contains("Memory Map for Token"));
            assertTrue(map.contains("#"), "should have filled chars");
            assertTrue(map.contains("."), "should have empty chars for partial slot");
            assertTrue(map.contains("100.0%"), "uint256 should be 100%");
        }

        @Test @DisplayName("visualizeAll multi-contract")
        void visualizeAll() {
            Map<String, ContractLayout> layouts = new LinkedHashMap<>();
            layouts.put("Token", makeLayout("Token", "owner", "Address", "20"));
            layouts.put("Vault", makeLayout("Vault", "balance", "uint256", "32"));

            String all = StorageLayoutVisualizer.visualizeAll(layouts);
            assertTrue(all.contains("2 contracts"));
            assertTrue(all.contains("Token"));
            assertTrue(all.contains("Vault"));
            assertTrue(all.contains("Total across all contracts"));
        }

        @Test @DisplayName("visualizeAll empty")
        void visualizeAllEmpty() {
            String result = StorageLayoutVisualizer.visualizeAll(Map.of());
            assertEquals("(no contract layouts)", result);
        }

        @Test @DisplayName("describeSlot formats correctly")
        void describeSlot() {
            SlotInfo slot = new SlotInfo("owner", "Address", 0, 20);
            String desc = StorageLayoutVisualizer.describeSlot(slot);
            assertTrue(desc.contains("Slot 0"));
            assertTrue(desc.contains("Address"));
            assertTrue(desc.contains("owner"));
            assertTrue(desc.contains("20 bytes"));
            assertTrue(desc.contains("62.5%"));
        }

        @Test @DisplayName("describeSlot null")
        void describeSlotNull() {
            assertEquals("(no slot)", StorageLayoutVisualizer.describeSlot(null));
        }

        @Test @DisplayName("efficiencyRating levels")
        void efficiencyRating() {
            // All 32-byte fields → 100% → Excellent
            ContractLayout full = makeLayout("A", "x", "uint256", "32");
            assertTrue(StorageLayoutVisualizer.efficiencyRating(full).contains("Excellent"));

            // 20-byte fields → 62.5% → Fair
            ContractLayout partial = makeLayout("B", "x", "Address", "20");
            assertTrue(StorageLayoutVisualizer.efficiencyRating(partial).contains("Fair"));

            // Empty
            ContractLayout empty = makeLayout("C");
            assertTrue(StorageLayoutVisualizer.efficiencyRating(empty).contains("N/A"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  5.  CallGraphGenerator
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("CallGraphGenerator")
    class CallGraphGeneratorTests {

        private CallGraphGenerator generator;

        @BeforeEach
        void init() {
            generator = new CallGraphGenerator();
        }

        @Test @DisplayName("empty program produces empty graph")
        void emptyProgram() {
            generator.analyze(new Program(List.of()));
            assertTrue(generator.getAllFunctions().isEmpty());
            assertEquals(0, generator.getEdgeCount());
        }

        @Test @DisplayName("manual edge addition")
        void manualEdges() {
            generator.addEdge("A.foo", "A.bar");
            generator.addEdge("A.foo", "B.baz");
            generator.addEdge("A.bar", "B.baz");

            assertEquals(3, generator.getEdgeCount());
            assertEquals(Set.of("A.bar", "B.baz"), generator.getCalleesOf("A.foo"));
            assertEquals(Set.of("A.foo", "A.bar"), generator.getCallersOf("B.baz"));
        }

        @Test @DisplayName("root and leaf detection")
        void rootsAndLeaves() {
            generator.addEdge("main", "helper");
            generator.addEdge("helper", "util");

            Set<String> roots = generator.getRootFunctions();
            assertTrue(roots.contains("main"), "main has no callers");
            assertFalse(roots.contains("helper"), "helper is called by main");

            Set<String> leaves = generator.getLeafFunctions();
            assertTrue(leaves.contains("util"), "util calls nothing");
            assertFalse(leaves.contains("main"), "main calls helper");
        }

        @Test @DisplayName("self-recursion cycle detection")
        void selfRecursion() {
            generator.addEdge("fib", "fib");

            List<List<String>> cycles = generator.findRecursiveCycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).contains("fib"));
        }

        @Test @DisplayName("mutual recursion cycle detection")
        void mutualRecursion() {
            generator.addEdge("A", "B");
            generator.addEdge("B", "A");

            List<List<String>> cycles = generator.findRecursiveCycles();
            assertFalse(cycles.isEmpty(), "should detect mutual recursion");
        }

        @Test @DisplayName("no cycles in DAG")
        void noCyclesInDAG() {
            generator.addEdge("A", "B");
            generator.addEdge("A", "C");
            generator.addEdge("B", "D");
            generator.addEdge("C", "D");

            List<List<String>> cycles = generator.findRecursiveCycles();
            assertTrue(cycles.isEmpty(), "DAG should have no cycles");
        }

        @Test @DisplayName("toDotFormat produces valid DOT")
        void toDotFormat() {
            generator.addEdge("main", "helper");
            String dot = generator.toDotFormat();
            assertTrue(dot.contains("digraph CallGraph"));
            assertTrue(dot.contains("\"main\" -> \"helper\""));
            assertTrue(dot.contains("}"));
        }

        @Test @DisplayName("toDotFormat self-edge highlighted")
        void toDotSelfEdge() {
            generator.addEdge("rec", "rec");
            String dot = generator.toDotFormat();
            assertTrue(dot.contains("color=red"));
        }

        @Test @DisplayName("toAsciiFormat readable output")
        void toAsciiFormat() {
            generator.addEdge("A.foo", "A.bar");
            generator.addEdge("A.bar", "C.baz");
            String ascii = generator.toAsciiFormat();
            assertTrue(ascii.contains("Call Graph"));
            assertTrue(ascii.contains("A.foo"));
            assertTrue(ascii.contains("→ A.bar"));
        }

        @Test @DisplayName("toAsciiFormat empty graph")
        void toAsciiEmpty() {
            String ascii = generator.toAsciiFormat();
            assertTrue(ascii.contains("empty"));
        }

        @Test @DisplayName("analyze program with function calls in body")
        void analyzeWithCalls() {
            // Build: class Foo { fn a() { b(); } fn b() { } }
            // a calls b via a CallExpr with VariableExpr callee
            dhrlang.lexer.Token bToken = new dhrlang.lexer.Token(
                    dhrlang.lexer.TokenType.IDENTIFIER, "b", 1);
            CallExpr callB = new CallExpr(new VariableExpr(bToken), List.of());
            ExpressionStmt callStmt = new ExpressionStmt(callB);

            FunctionDecl fnA = makeFnWithBody("a", List.of(callStmt));
            FunctionDecl fnB = makeFn("b");
            ClassDecl foo = makeClass("Foo", fnA, fnB);

            generator.analyze(new Program(List.of(foo)));

            Set<String> calleesOfA = generator.getCalleesOf("Foo.a");
            assertTrue(calleesOfA.contains("b"), "a should call b");
        }

        @Test @DisplayName("getAllEdges returns CallEdge objects")
        void getAllEdges() {
            generator.addEdge("A", "B");
            generator.addEdge("A", "B"); // duplicate increases count

            List<CallGraphGenerator.CallEdge> edges = generator.getAllEdges();
            assertEquals(1, edges.size());
            assertEquals(2, edges.get(0).getCallCount());
        }

        @Test @DisplayName("clear resets graph")
        void clearResets() {
            generator.addEdge("A", "B");
            generator.clear();
            assertTrue(generator.getAllFunctions().isEmpty());
            assertEquals(0, generator.getEdgeCount());
        }
    }

    // ═══════════════════════════════════════════════════
    //  6.  ContractDebugger
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("ContractDebugger")
    class ContractDebuggerTests {

        @Test @DisplayName("STOP halts execution")
        void stopHalts() {
            // STOP = 0x00
            byte[] code = {0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            ContractDebugger.DebugState state = dbg.step();
            assertTrue(dbg.isHalted());
            assertEquals("STOP", dbg.getHaltReason());
        }

        @Test @DisplayName("PUSH1 + PUSH1 + ADD")
        void push1Add() {
            // PUSH1 3, PUSH1 5, ADD, STOP
            byte[] code = {0x60, 0x03, 0x60, 0x05, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);

            dbg.step(); // PUSH1 3 → stack: [3]
            assertEquals(1, dbg.getStackDepth());
            assertEquals(BigInteger.valueOf(3), dbg.peekStack());

            dbg.step(); // PUSH1 5 → stack: [5, 3]
            assertEquals(2, dbg.getStackDepth());
            assertEquals(BigInteger.valueOf(5), dbg.peekStack());

            dbg.step(); // ADD → stack: [8]
            assertEquals(1, dbg.getStackDepth());
            assertEquals(BigInteger.valueOf(8), dbg.peekStack());

            dbg.step(); // STOP
            assertTrue(dbg.isHalted());
        }

        @Test @DisplayName("PUSH1 + PUSH1 + SUB")
        void push1Sub() {
            // PUSH1 3, PUSH1 10, SUB → a=10(top) - b=3(second) = 7
            byte[] code = {0x60, 0x03, 0x60, 0x0A, 0x03, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); // PUSH1 3
            dbg.step(); // PUSH1 10
            dbg.step(); // SUB: 10 - 3 = 7
            assertEquals(BigInteger.valueOf(7), dbg.peekStack());
        }

        @Test @DisplayName("PUSH1 + PUSH1 + MUL")
        void push1Mul() {
            // PUSH1 6, PUSH1 7, MUL → 42
            byte[] code = {0x60, 0x06, 0x60, 0x07, 0x02, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); dbg.step(); dbg.step();
            assertEquals(BigInteger.valueOf(42), dbg.peekStack());
        }

        @Test @DisplayName("SSTORE and SLOAD")
        void storageOps() {
            // PUSH1 42, PUSH1 0, SSTORE → store 42 at slot 0
            // PUSH1 0, SLOAD → load from slot 0 → 42
            byte[] code = {
                0x60, 0x2A,       // PUSH1 42
                0x60, 0x00,       // PUSH1 0
                0x55,             // SSTORE
                0x60, 0x00,       // PUSH1 0
                0x54,             // SLOAD
                0x00              // STOP
            };
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.runToEnd();
            assertTrue(dbg.isHalted());
            assertEquals(BigInteger.valueOf(42), dbg.readStorage(BigInteger.ZERO));
        }

        @Test @DisplayName("MSTORE and MLOAD")
        void memoryOps() {
            // PUSH1 0xFF, PUSH1 0, MSTORE → store 0xFF at offset 0
            // PUSH1 0, MLOAD → load from offset 0
            byte[] code = {
                0x60, (byte) 0xFF, // PUSH1 255
                0x60, 0x00,        // PUSH1 0
                0x52,              // MSTORE
                0x60, 0x00,        // PUSH1 0
                0x51,              // MLOAD
                0x00               // STOP
            };
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.runToEnd();
            // The value stored was 0xFF, which fills the last byte of the 32-byte word
            BigInteger memValue = dbg.readMemory(0);
            assertEquals(BigInteger.valueOf(255), memValue);
        }

        @Test @DisplayName("ISZERO operations")
        void iszero() {
            // PUSH1 0, ISZERO → 1; PUSH1 5, ISZERO → 0
            byte[] code = {0x60, 0x00, 0x15, 0x60, 0x05, 0x15, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); // PUSH1 0
            dbg.step(); // ISZERO → 1
            assertEquals(BigInteger.ONE, dbg.peekStack());
            dbg.step(); // PUSH1 5
            dbg.step(); // ISZERO → 0
            assertEquals(BigInteger.ZERO, dbg.peekStack());
        }

        @Test @DisplayName("EQ operations")
        void equality() {
            // PUSH1 5, PUSH1 5, EQ → 1
            byte[] code = {0x60, 0x05, 0x60, 0x05, 0x14, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); dbg.step(); dbg.step();
            assertEquals(BigInteger.ONE, dbg.peekStack());
        }

        @Test @DisplayName("breakpoints work")
        void breakpoints() {
            // PUSH1 1, PUSH1 2, ADD, STOP
            byte[] code = {0x60, 0x01, 0x60, 0x02, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);

            // Set breakpoint at ADD (offset 4)
            dbg.setBreakpoint(4);
            ContractDebugger.DebugState state = dbg.continueExecution();

            // Should stop at breakpoint, not halted
            assertFalse(state.isHalted());
            assertEquals(4, state.getPc());
            assertEquals(EvmOpcode.ADD, dbg.getCurrentOpcode());

            // Continue past breakpoint
            state = dbg.continueExecution();
            assertTrue(state.isHalted()); // runs to STOP
        }

        @Test @DisplayName("breakpoint management")
        void breakpointManagement() {
            ContractDebugger dbg = new ContractDebugger(new byte[]{0x00});
            dbg.setBreakpoint(0);
            dbg.setBreakpoint(5);
            dbg.setBreakpoint(10);
            assertEquals(3, dbg.getBreakpoints().size());

            dbg.removeBreakpoint(5);
            assertEquals(2, dbg.getBreakpoints().size());
            assertFalse(dbg.getBreakpoints().contains(5));

            dbg.clearBreakpoints();
            assertTrue(dbg.getBreakpoints().isEmpty());
        }

        @Test @DisplayName("gas accounting")
        void gasAccounting() {
            // PUSH1 + ADD + STOP
            byte[] code = {0x60, 0x01, 0x60, 0x02, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.runToEnd();
            // PUSH1=3 + PUSH1=3 + ADD=3 + STOP=0 = 9
            assertEquals(9, dbg.getGasUsed());
        }

        @Test @DisplayName("disassembly output")
        void disassembly() {
            byte[] code = {0x60, 0x01, 0x60, 0x02, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            String disasm = dbg.getDisassembly(0, 6);
            assertTrue(disasm.contains("PUSH1"));
            assertTrue(disasm.contains("ADD"));
            assertTrue(disasm.contains("STOP"));
        }

        @Test @DisplayName("full disassembly")
        void fullDisassembly() {
            byte[] code = {0x60, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            String full = dbg.getFullDisassembly();
            assertTrue(full.contains("PUSH1"));
            assertTrue(full.contains("STOP"));
        }

        @Test @DisplayName("history tracking")
        void historyTracking() {
            byte[] code = {0x60, 0x01, 0x60, 0x02, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.runToEnd();
            List<ContractDebugger.DebugState> history = dbg.getHistory();
            assertFalse(history.isEmpty());
            // Each step produces a history entry
            assertTrue(history.size() >= 3); // at least PUSH1, PUSH1, ADD
        }

        @Test @DisplayName("reset restores initial state")
        void resetRestores() {
            byte[] code = {0x60, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.runToEnd();
            assertTrue(dbg.isHalted());

            dbg.reset();
            assertFalse(dbg.isHalted());
            assertEquals(0, dbg.getPc());
            assertEquals(0, dbg.getStackDepth());
            assertEquals(0, dbg.getGasUsed());
            assertEquals(0, dbg.getStepNumber());
        }

        @Test @DisplayName("end of bytecode halts gracefully")
        void endOfBytecode() {
            byte[] code = {0x60, 0x01}; // PUSH1 1, no STOP
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); // PUSH1

            // PC is now beyond bytecode
            ContractDebugger.DebugState state = dbg.step();
            assertTrue(state.isHalted());
            assertTrue(dbg.getHaltReason().contains("End of bytecode"));
        }

        @Test @DisplayName("POP operation")
        void popOperation() {
            byte[] code = {0x60, 0x01, 0x50, 0x00}; // PUSH1 1, POP, STOP
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); // PUSH1 1
            assertEquals(1, dbg.getStackDepth());
            dbg.step(); // POP
            assertEquals(0, dbg.getStackDepth());
        }

        @Test @DisplayName("DUP1 duplicates top of stack")
        void dup1() {
            // PUSH1 42, DUP1, STOP
            byte[] code = {0x60, 0x2A, (byte) 0x80, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); // PUSH1 42
            dbg.step(); // DUP1
            assertEquals(2, dbg.getStackDepth());
            List<BigInteger> stack = dbg.getStack();
            assertEquals(BigInteger.valueOf(42), stack.get(0));
            assertEquals(BigInteger.valueOf(42), stack.get(1));
        }

        @Test @DisplayName("REVERT halts with REVERT reason")
        void revert() {
            // PUSH0, PUSH0, REVERT
            byte[] code = {0x5F, 0x5F, (byte) 0xFD};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.runToEnd();
            assertTrue(dbg.isHalted());
            assertEquals("REVERT", dbg.getHaltReason());
        }

        @Test @DisplayName("DebugState toString is informative")
        void debugStateToString() {
            byte[] code = {0x60, 0x01, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            ContractDebugger.DebugState state = dbg.step();
            String str = state.toString();
            assertTrue(str.contains("Step"));
            assertTrue(str.contains("PC="));
        }

        @Test @DisplayName("PUSH2 loads 2-byte value")
        void push2() {
            // PUSH2 0x01 0x00 = 256
            byte[] code = {0x61, 0x01, 0x00, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step();
            assertEquals(BigInteger.valueOf(256), dbg.peekStack());
        }

        @Test @DisplayName("LT comparison")
        void ltComparison() {
            // PUSH1 3, PUSH1 5, LT → 3 < 5 = 0 (note: a=PUSH 3, b=PUSH 5 → a < b?)
            // Actually: stack pops a then b. a = top (5), b = second (3)
            // LT: a < b → 5 < 3 → false → 0
            byte[] code = {0x60, 0x03, 0x60, 0x05, 0x10, 0x00};
            ContractDebugger dbg = new ContractDebugger(code);
            dbg.step(); dbg.step(); dbg.step(); // PUSH 3, PUSH 5, LT
            assertEquals(BigInteger.ZERO, dbg.peekStack()); // 5 < 3 = false
        }
    }

    // ═══════════════════════════════════════════════════
    //  7.  Integration Tests
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("Integration")
    class IntegrationTests {

        @Test @DisplayName("TraceRecorder + GasProfiler combined workflow")
        void traceAndProfile() {
            TraceRecorder trace = new TraceRecorder();
            GasProfiler profiler = new GasProfiler();

            trace.enable();

            // Simulate a contract call
            trace.recordCall("transfer", "Token", List.of("0xABC", 100L));
            profiler.startFunction("transfer");

            profiler.recordOpcode(EvmOpcode.SLOAD);  // load balance
            trace.recordStateChange("balance[sender]", 1000L, 900L);

            profiler.recordOpcode(EvmOpcode.SSTORE); // store new balance
            trace.recordStateChange("balance[receiver]", 0L, 100L);

            profiler.recordOpcode(EvmOpcode.SSTORE); // store receiver balance
            trace.recordLogEvent("Transfer", "from=0xABC, to=0xDEF, amount=100");

            profiler.endFunction("transfer");
            trace.recordReturn("transfer", true);

            // Verify trace
            assertEquals(5, trace.getEntryCount()); // call + 2 state + 1 log + return
            assertEquals(1, trace.getEntriesOfType(TraceRecorder.EntryType.LOG_EVENT).size());

            // Verify profiler
            assertEquals(300, profiler.getTotalGas()); // SLOAD(100) + 2*SSTORE(100)
            assertEquals("transfer", profiler.getHotspots(1).get(0).getFunctionName());
        }

        @Test @DisplayName("StorageLayoutVisualizer with real StorageLayouter")
        void visualizerWithLayouter() {
            // Create a contract class with storage fields
            List<VarDecl> vars = new ArrayList<>();
            vars.add(new VarDecl("Address", "owner", null, Set.of(),
                    EnumSet.of(ContractAnnotation.STORAGE)));
            vars.add(new VarDecl("uint256", "totalSupply", null, Set.of(),
                    EnumSet.of(ContractAnnotation.STORAGE)));

            ClassDecl contract = new ClassDecl("Token", null, List.of(), List.of(),
                    vars, Set.of(), EnumSet.of(ContractAnnotation.CONTRACT));

            Program program = new Program(List.of(contract));

            StorageLayouter layouter = new StorageLayouter();
            layouter.layoutAll(program);

            ContractLayout layout = layouter.getLayout("Token");
            assertNotNull(layout);

            // Visualize
            String vis = StorageLayoutVisualizer.visualize(layout);
            assertTrue(vis.contains("owner"));
            assertTrue(vis.contains("totalSupply"));
            assertTrue(vis.contains("2 slots"));

            String memMap = StorageLayoutVisualizer.visualizeMemoryMap(layout);
            assertTrue(memMap.contains("owner"));
            assertTrue(memMap.contains("#"));

            String efficiency = StorageLayoutVisualizer.efficiencyRating(layout);
            assertFalse(efficiency.contains("N/A")); // has fields
        }

        @Test @DisplayName("Debugger with EvmCodeBuffer-generated bytecode")
        void debuggerWithCodeBuffer() {
            EvmCodeBuffer buf = new EvmCodeBuffer();

            // Generate: PUSH1 10, PUSH1 20, ADD, PUSH1 0, SSTORE, STOP
            buf.push1(10);
            buf.push1(20);
            buf.emit(EvmOpcode.ADD);
            buf.push1(0);
            buf.emit(EvmOpcode.SSTORE);
            buf.emit(EvmOpcode.STOP);

            byte[] bytecode = buf.toByteArray();
            ContractDebugger dbg = new ContractDebugger(bytecode);

            // Run to completion
            dbg.runToEnd();
            assertTrue(dbg.isHalted());

            // Verify storage: slot 0 should have 30
            assertEquals(BigInteger.valueOf(30), dbg.readStorage(BigInteger.ZERO));
        }

        @Test @DisplayName("CallGraphGenerator with nested method calls")
        void callGraphNestedCalls() {
            // class Token { transfer() calls _checkBalance(); _checkBalance() calls require(); }
            CallGraphGenerator graph = new CallGraphGenerator();
            graph.addEdge("Token.transfer", "Token._checkBalance");
            graph.addEdge("Token._checkBalance", "require");
            graph.addEdge("Token.transfer", "Token._updateBalance");

            String dot = graph.toDotFormat();
            assertTrue(dot.contains("Token.transfer"));
            assertTrue(dot.contains("Token._checkBalance"));
            assertTrue(dot.contains("require"));

            // transfer is root, require is leaf
            assertTrue(graph.getRootFunctions().contains("Token.transfer"));
            assertTrue(graph.getLeafFunctions().contains("require"));
            assertTrue(graph.getLeafFunctions().contains("Token._updateBalance"));

            // No cycles
            assertTrue(graph.findRecursiveCycles().isEmpty());
        }

        @Test @DisplayName("Full debug workflow: compile → debug → profile")
        void fullDebugWorkflow() {
            // 1. Compile bytecode
            EvmCodeBuffer buf = new EvmCodeBuffer();
            buf.push1(100);     // value
            buf.push1(0);       // slot
            buf.emit(EvmOpcode.SSTORE);  // store 100 at slot 0
            buf.push1(50);      // push 50 first (will be second on stack)
            buf.push1(0);       // slot
            buf.emit(EvmOpcode.SLOAD);   // load slot 0 → pushes 100 on top
            buf.emit(EvmOpcode.SUB);     // a=100(top) - b=50(second) = 50
            buf.push1(0);       // slot
            buf.emit(EvmOpcode.SSTORE);  // store 50 at slot 0
            buf.emit(EvmOpcode.STOP);

            byte[] bytecode = buf.toByteArray();

            // 2. Profile
            GasProfiler profiler = new GasProfiler();
            profiler.startFunction("withdraw");
            profiler.recordOpcode(EvmOpcode.SSTORE);
            profiler.recordOpcode(EvmOpcode.SLOAD);
            profiler.recordOpcode(EvmOpcode.SUB);
            profiler.recordOpcode(EvmOpcode.SSTORE);
            profiler.endFunction("withdraw");

            // 3. Debug execution
            ContractDebugger dbg = new ContractDebugger(bytecode);
            dbg.runToEnd();
            assertTrue(dbg.isHalted());
            assertEquals(BigInteger.valueOf(50), dbg.readStorage(BigInteger.ZERO));

            // 4. Verify profiled gas
            // SSTORE(100) + SLOAD(100) + SUB(3) + SSTORE(100) = 303
            assertEquals(303, profiler.getTotalGas());

            // 5. Get disassembly
            String disasm = dbg.getFullDisassembly();
            assertTrue(disasm.contains("SSTORE"));
            assertTrue(disasm.contains("SLOAD"));
        }
    }
}
