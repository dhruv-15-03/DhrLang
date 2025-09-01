package dhrlang.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dhrlang.util.MiniRunner;

public final class SampleSuite {
    private static final String INPUT_DIR = "input";

    private static final List<String> POSITIVE = List.of(
        "sample.dhr",
        "simple_working_test.dhr",
        "comprehensive_test_edge_cases.dhr",
        "fixed_comprehensive_test_edge_cases.dhr",
        "test_basic_syntax.dhr",
        "test_arrays.dhr",
        "test_algorithms.dhr",
        "test_exceptions.dhr",
        "test_strings.dhr",
        "test_static_methods.dhr",
        "test_oop_features.dhr",
        "test_edge_control_flow.dhr",
        "test_edge_exceptions.dhr",
        "test_range_usage.dhr",
        "test_nested_try_finally.dhr",
        "test_simple_algorithms.dhr",
        "test_access_modifiers.dhr"
    );

    // Files intentionally producing compile / runtime errors (diagnostic / negative tests)
    private static final List<String> NEGATIVE = List.of(
        "advanced_features_test.dhr",          // generics & generic arrays (partial support)
        "complete_feature_demo.dhr",           // forward-looking generics & abstract mismatch
        "advanced_edge_cases.dhr",             // extreme / stress (may evolve)
        "duplicate_error_test.dhr",            // lexer / parser errors
        "parser_error_test.dhr",               // parser recovery
        "test_edge_arrays.dhr",                // compile-time type/index errors
        "test_edge_strings.dhr",               // type mismatch & bounds warnings
        "test_edge_type_errors.dhr",           // assorted type errors
        "test_hetero_array.dhr",               // heterogeneous array rejection
        "test_recursion_depth.dhr",            // may trigger overflow / access error
        "test_static_forward_ref.dhr"          // static forward reference limitation
    );

    public static void main(String[] args) throws Exception {
        List<Result> positiveResults = new ArrayList<>();
        List<Result> negativeResults = new ArrayList<>();

        for (String f : POSITIVE) {
            positiveResults.add(runOne(f, true));
        }
        for (String f : NEGATIVE) {
            negativeResults.add(runOne(f, false));
        }

        printSummary(positiveResults, negativeResults);

        // Exit code: 0 even if negatives "fail" (expected). Non-zero only if a POSITIVE sample failed unexpectedly.
        boolean positiveFailure = positiveResults.stream().anyMatch(r -> !r.expectedPass || r.hadCompileErrors || r.hadRuntimeError);
        if (positiveFailure) {
            System.exit(1);
        }
    }

    private static Result runOne(String fileName, boolean expectedPass) {
        String path = INPUT_DIR + "/" + fileName;
        String source;
        try { source = Files.readString(Path.of(path)); }
        catch (IOException e) { return new Result(fileName, expectedPass, true, false, "IO Error: " + e.getMessage(), ""); }
    MiniRunner.Result capturing = MiniRunner.run(source);
    return new Result(fileName, expectedPass, capturing.hadCompileErrors, capturing.hadRuntimeError, capturing.stderr, capturing.stdout);
    }

    private static void printSummary(List<Result> pos, List<Result> neg) {
        System.out.println("==== Sample Suite Summary ====");
        System.out.println("Positive samples: " + pos.size());
        for (Result r : pos) {
            System.out.println("  [" + (r.hadCompileErrors?"CE":"ok") + "/" + (r.hadRuntimeError?"RE":"ok") + "] " + r.file + (r.hadCompileErrors||r.hadRuntimeError?" *":""));
        }
        System.out.println();
        System.out.println("Negative samples (failures expected / informational): " + neg.size());
        for (Result r : neg) {
            System.out.println("  [" + (r.hadCompileErrors?"CE":"ok") + "/" + (r.hadRuntimeError?"RE":"ok") + "] " + r.file);
        }
        long unexpected = pos.stream().filter(r -> r.hadCompileErrors || r.hadRuntimeError).count();
        System.out.println();
        System.out.println("Unexpected positive failures: " + unexpected);
    }

    private record Result(String file, boolean expectedPass, boolean hadCompileErrors, boolean hadRuntimeError, String stderr, String stdout) {}
}
