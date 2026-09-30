package dhrlang.learn;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dhrlang.host.HostExecution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Offline practice graded by bounded execution rather than a model's opinion. */
public final class LearnCli {
    private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();
    private static final int MAX_REPORT_BYTES = 2 * 1024 * 1024;
    private static final HostExecution.Limits LIMITS = new HostExecution.Limits(5000, 1_000_000, 128, 4096);

    public record Case(String name, String input, String expected) {
        public Case {
            require(text(name), "Case name must not be blank");
            require(input != null && input.length() <= 65536, "Invalid case input");
            require(expected != null && expected.length() <= 4096, "Invalid expected output");
        }
    }

    public record Exercise(String id, String title, String goal, String starter,
                           List<String> hints, List<Case> cases, String transfer) {
        public Exercise {
            require(validId(id), "Invalid exercise identifier: " + id);
            require(text(title) && text(goal) && text(starter) && text(transfer), "Incomplete exercise metadata");
            require(hints != null && hints.size() == 3 && hints.stream().allMatch(LearnCli::text),
                    "An exercise must have three nonblank hints");
            require(cases != null && !cases.isEmpty() && cases.size() <= 5
                    && cases.stream().allMatch(java.util.Objects::nonNull), "An exercise must have 1..5 cases");
            require(cases.stream().map(Case::name).distinct().count() == cases.size(), "Duplicate case names");
            hints = List.copyOf(hints);
            cases = List.copyOf(cases);
        }
    }

    public record Catalog(int schemaVersion, List<Exercise> exercises) {
        public Catalog {
            require(schemaVersion == 1, "Unknown exercise catalog schemaVersion");
            require(exercises != null && !exercises.isEmpty() && exercises.size() <= 30, "Invalid exercise catalog size");
            var identifiers = new HashSet<String>();
            for (Exercise exercise : exercises) {
                require(exercise != null && identifiers.add(exercise.id()), "Null or duplicate exercise");
            }
            exercises = List.copyOf(exercises);
        }
    }

    public record CaseResult(String name, String input, boolean passed, HostExecution.Status status,
                             String expected, String actual, String stderr,
                             List<HostExecution.Diagnostic> diagnostics, String message) {
        public CaseResult {
            require(text(name) && input != null && status != null && expected != null && actual != null
                    && stderr != null && diagnostics != null && text(message), "Incomplete case result");
            require(passed == (status == HostExecution.Status.SUCCESS && expected.equals(actual) && stderr.isEmpty()),
                    "Case pass flag does not match its execution evidence");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record Result(int schemaVersion, String compilerVersion, String profile, String exercise, String sourceSha256,
                         int passed, int total, List<CaseResult> cases, String transfer) {
        public Result {
            require(schemaVersion == 1 && text(compilerVersion) && HostExecution.PROFILE.equals(profile)
                    && validId(exercise) && sourceSha256 != null && sourceSha256.matches("[0-9a-f]{64}")
                    && text(transfer), "Invalid grading identity");
            require(cases != null && !cases.isEmpty() && cases.size() <= 5
                    && cases.stream().allMatch(java.util.Objects::nonNull), "Invalid grading cases");
            require(total == cases.size() && passed == cases.stream().filter(CaseResult::passed).count(),
                    "Grading totals do not match the cases");
            cases = List.copyOf(cases);
        }
    }

    public record TraceResult(int schemaVersion, String mode, String exercise, String caseName,
                              String input, String expected, HostExecution.TracedResponse result) {}

    private LearnCli() {}

    public static Catalog catalog() throws IOException {
        try (InputStream stream = LearnCli.class.getResourceAsStream("/dhrlang/learn/exercises.json")) {
            if (stream == null) throw new IOException("The packaged exercise catalog is missing; rebuild stageCompiler");
            return readCatalog(stream);
        }
    }

    static Catalog readCatalog(InputStream stream) throws IOException {
        return readJson(stream, Catalog.class, 128 * 1024);
    }

    private static <T> T readJson(InputStream stream, Class<T> type, int maximum) throws IOException {
        byte[] bytes = stream.readNBytes(maximum + 1);
        if (bytes.length > maximum) throw new IOException("JSON input exceeds " + maximum + " bytes");
        T result = JSON.readValue(bytes, type);
        if (result == null) throw new IOException("Expected a JSON object, not null");
        return result;
    }

    public static Exercise exercise(String id) throws IOException {
        return catalog().exercises().stream().filter(exercise -> exercise.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown exercise: " + id + ". Use learn list."));
    }

    public static void start(Exercise exercise, Path destination) throws IOException {
        Files.writeString(destination, exercise.starter(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public static Result check(Exercise exercise, String source, Path compiler) throws IOException, InterruptedException {
        List<CaseResult> results = new ArrayList<>();
        String identity = null;
        String compilerVersion = null;
        int passed = 0;
        for (Case sample : exercise.cases()) {
            var request = new HostExecution.Request(1, HostExecution.PROFILE, source, sample.input(), LIMITS);
            var execution = HostExecution.execute(request, compiler);
            identity = execution.sourceSha256();
            compilerVersion = execution.compilerVersion();
            String output = normalizeOutput(execution.stdout());
            boolean success = execution.status() == HostExecution.Status.SUCCESS
                    && output.equals(sample.expected()) && execution.stderr().isEmpty();
            String message = execution.message();
            if (execution.status() == HostExecution.Status.SUCCESS) {
                message = success ? "Matched the expected output" : execution.stderr().isEmpty()
                        ? "Output does not match the expected value" : "Program wrote to stderr";
            }
            if (success) passed++;
            results.add(new CaseResult(sample.name(), sample.input(), success, execution.status(),
                    sample.expected(), output, execution.stderr(), execution.diagnostics(), message));
        }
        return new Result(1, compilerVersion, HostExecution.PROFILE, exercise.id(), identity,
                passed, results.size(), results, exercise.transfer());
    }

    static String normalizeOutput(String output) {
        String normalized = output.replace("\r\n", "\n");
        return normalized.endsWith("\n") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    public static void save(Result result, Path destination) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(result);
        if (bytes.length > MAX_REPORT_BYTES) throw new IOException("Grading report exceeds " + MAX_REPORT_BYTES + " bytes");
        Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public static Result readResult(Path report) throws IOException {
        if (!Files.isRegularFile(report)) throw new IOException("Report must be a regular JSON file: " + report);
        try (InputStream stream = Files.newInputStream(report)) {
            return readJson(stream, Result.class, MAX_REPORT_BYTES);
        }
    }

    static int exitCode(Result result) {
        if (result.cases().stream().anyMatch(sample -> sample.status() == HostExecution.Status.WORKER_ERROR
                || sample.status() == HostExecution.Status.INVALID_REQUEST)) return 2;
        return result.passed() == result.total() ? 0 : 1;
    }

    public static int runCli(String[] args) {
        try {
            require(args.length >= 2, usage());
            switch (args[1]) {
                case "enterprise" -> {
                    return dhrlang.enterprise.EnterpriseCli.runCli(java.util.Arrays.copyOfRange(args, 1, args.length));
                }
                case "list" -> {
                    require(args.length == 2, usage());
                    for (Exercise exercise : catalog().exercises()) {
                        System.out.println(exercise.id() + "  " + exercise.title());
                    }
                }
                case "show" -> {
                    require(args.length == 3, usage());
                    Exercise exercise = exercise(args[2]);
                    System.out.println(exercise.id() + ": " + exercise.title());
                    System.out.println(exercise.goal());
                    System.out.println("\nPredict the result, edit the starter, then use learn check.\n");
                    System.out.print(exercise.starter());
                    System.out.println("\nPractice cases (stdin -> expected stdout):");
                    for (Case sample : exercise.cases()) {
                        System.out.println("  " + sample.name() + ": " + JSON.writeValueAsString(sample.input())
                                + " -> " + JSON.writeValueAsString(sample.expected()));
                    }
                    System.out.println("Transfer task: " + exercise.transfer());
                }
                case "hint" -> {
                    require(args.length == 3 || args.length == 4, usage());
                    Exercise exercise = exercise(args[2]);
                    String levelText = args.length == 4 ? args[3] : "1";
                    require(levelText.matches("[1-3]"), "Hint level must be 1, 2 or 3");
                    System.out.println(exercise.hints().get(Integer.parseInt(levelText) - 1));
                }
                case "start" -> {
                    require(args.length == 4, usage());
                    Exercise exercise = exercise(args[2]);
                    start(exercise, Path.of(args[3]));
                    System.out.println("Created " + args[3] + ". Existing files are never overwritten.");
                    System.out.println(exercise.goal());
                }
                case "check" -> {
                    require(args.length >= 4, usage());
                    Exercise exercise = exercise(args[2]);
                    boolean json = false;
                    Path record = null;
                    for (int i = 4; i < args.length; i++) {
                        if ("--json".equals(args[i]) && !json) {
                            json = true;
                        } else if ("--record".equals(args[i]) && record == null && i + 1 < args.length) {
                            require(!args[i + 1].startsWith("--"), "--record requires a new result file");
                            record = Path.of(args[++i]);
                        } else {
                            throw new IllegalArgumentException(usage());
                        }
                    }
                    Path compiler = packagedCompiler();
                    if (record != null && Files.exists(record)) throw new IOException("Report already exists; choose a new file: " + record);
                    String source = readSubmission(Path.of(args[3]));
                    Result result = check(exercise, source, compiler);
                    if (record != null) save(result, record);
                    if (json) System.out.println(JSON.writeValueAsString(result));
                    else printResult(result);
                    return exitCode(result);
                }
                case "trace" -> {
                    require(args.length == 5 || (args.length == 6 && "--json".equals(args[5])),
                            "Usage: learn trace <id> <file.dhr> <case-number> [--json]");
                    Exercise exercise = exercise(args[2]);
                    require(args[4].matches("[1-5]"), "Case number must identify one of the displayed practice cases");
                    int selected = Integer.parseInt(args[4]) - 1;
                    require(selected < exercise.cases().size(), "Case number exceeds the exercise's case count");
                    Case sample = exercise.cases().get(selected);
                    String source = readSubmission(Path.of(args[3]));
                    var execution = HostExecution.executeTraced(new HostExecution.Request(
                            1, HostExecution.PROFILE, source, sample.input(), LIMITS), packagedCompiler());
                    var result = new TraceResult(1, "unoptimized-bytecode-trace", exercise.id(),
                            sample.name(), sample.input(), sample.expected(), execution);
                    if (args.length == 6) {
                        System.out.println(JSON.writeValueAsString(result));
                    } else {
                        System.out.println("Trace of " + sample.name() + "; instructions observed before execution, no variable values.");
                        System.out.println("Unoptimized bytecode; use learn check to grade the normal optimized program.");
                        System.out.println("Source SHA-256: " + execution.execution().sourceSha256());
                        for (var step : execution.trace().steps()) {
                            System.out.println("#" + step.sequence() + " " + step.file() + ":" + step.line() + ":" + step.column()
                                    + " " + step.function() + " pc=" + step.instruction() + " " + step.opcode()
                                    + " depth=" + step.depth());
                        }
                        System.out.println("Trace truncated: " + execution.trace().truncated());
                        System.out.println("Execution: " + execution.execution().status() + " - " + execution.execution().message());
                        System.out.println("Expected: " + JSON.writeValueAsString(sample.expected()));
                        System.out.println("Actual:   " + JSON.writeValueAsString(normalizeOutput(execution.execution().stdout())));
                        for (var diagnostic : execution.execution().diagnostics()) System.out.println(diagnostic.message());
                        if (!execution.execution().stderr().isEmpty()) {
                            System.out.println("Stderr: " + JSON.writeValueAsString(execution.execution().stderr()));
                        }
                    }
                    return execution.execution().status() == HostExecution.Status.SUCCESS ? 0 : 2;
                }
                case "progress" -> {
                    require(args.length >= 3 && args.length <= 32, "Usage: learn progress <result.json> [more-results.json ...]");
                    List<Result> reports = new ArrayList<>();
                    for (int i = 2; i < args.length; i++) reports.add(readResult(Path.of(args[i])));
                    System.out.println("Recorded practice results (not revalidated against current source):");
                    for (Result report : reports) {
                        System.out.println(report.exercise() + ": " + report.passed() + "/" + report.total()
                                + " cases passed; source " + report.sourceSha256());
                    }
                }
                default -> throw new IllegalArgumentException(usage());
            }
            return 0;
        } catch (IllegalArgumentException failure) {
            System.err.println("Learning command rejected: " + failure.getMessage());
            return 64;
        } catch (IOException | java.net.URISyntaxException failure) {
            System.err.println("Learning command failed: " + failure.getMessage());
            return 2;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            System.err.println("Learning command interrupted");
            return 2;
        }
    }

    private static Path packagedCompiler() throws IOException, java.net.URISyntaxException {
        Path compiler = Path.of(LearnCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(compiler)) throw new IOException("Learning execution requires the packaged compiler; build stageCompiler");
        return compiler;
    }

    private static String readSubmission(Path submission) throws IOException {
        if (!Files.isRegularFile(submission)) throw new IOException("Submission must be a regular UTF-8 file: " + submission);
        try (InputStream input = Files.newInputStream(submission)) {
            byte[] bytes = input.readNBytes(HostExecution.MAX_REQUEST_BYTES + 1);
            require(bytes.length <= HostExecution.MAX_REQUEST_BYTES, "Submission is too large");
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private static void printResult(Result result) throws IOException {
        System.out.println(result.passed() + "/" + result.total() + " cases passed for " + result.exercise());
        for (CaseResult sample : result.cases()) {
            System.out.println((sample.passed() ? "PASS " : "FAIL ") + sample.name() + " [" + sample.status() + "]");
            if (!sample.passed()) {
                System.out.println("  Input:    " + JSON.writeValueAsString(sample.input()));
                System.out.println("  Expected: " + JSON.writeValueAsString(sample.expected()));
                System.out.println("  Actual:   " + JSON.writeValueAsString(sample.actual()));
                System.out.println("  " + sample.message());
                if (!sample.stderr().isEmpty()) System.out.println("  Stderr: " + JSON.writeValueAsString(sample.stderr()));
                for (var diagnostic : sample.diagnostics()) {
                    System.out.println("  " + diagnostic.code() + " at " + diagnostic.line() + ":" + diagnostic.column()
                            + ": " + diagnostic.message());
                }
            }
        }
        System.out.println("Transfer task: " + result.transfer());
    }

    private static String usage() {
        return "Usage: learn list | show <id> | hint <id> [1-3] | start <id> <new-file.dhr>\n"
                + "       learn check <id> <file.dhr> [--json] [--record <new-result.json>]\n"
                + "       learn trace <id> <file.dhr> <case-number> [--json]\n"
                + "       learn enterprise <cases|start|verify|demo>  Synthetic purchase workflow\n"
                + "       learn progress <result.json> [more-results.json ...]";
    }

    private static boolean validId(String id) {
        return id != null && id.length() <= 64 && id.matches("[0-9]{2}-[a-z]+(?:-[a-z]+)*");
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
