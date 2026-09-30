package dhrlang.host;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dhrlang.ast.Program;
import dhrlang.bytecode.BytecodeVM;
import dhrlang.bytecode.BytecodeWriter;
import dhrlang.error.DhrError;
import dhrlang.error.ErrorReporter;
import dhrlang.interpreter.DhrRuntimeException;
import dhrlang.ir.AstToIrLowerer;
import dhrlang.ir.opt.IrOptimizer;
import dhrlang.typechecker.TypeChecker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Versioned, process-separated execution for local tools and teaching adapters.
 * Resource limits and a restricted native surface are not an OS security sandbox.
 */
public final class HostExecution {
    public static final String PROFILE = "jvm-bytecode-v1";
    public static final int MAX_REQUEST_BYTES = 512 * 1024;
    private static final int MAX_SOURCE_CHARS = 128 * 1024;
    private static final int MAX_INPUT_CHARS = 64 * 1024;
    private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private HostExecution() {}

    public record Limits(int timeoutMs, int maxSteps, int heapMb, int maxOutputBytes) {
        public Limits {
            require(timeoutMs >= 1 && timeoutMs <= 30_000, "timeoutMs must be 1..30000");
            require(maxSteps >= 100 && maxSteps <= 5_000_000, "maxSteps must be 100..5000000");
            require(heapMb >= 32 && heapMb <= 256, "heapMb must be 32..256");
            require(maxOutputBytes >= 256 && maxOutputBytes <= 1_048_576, "maxOutputBytes must be 256..1048576");
        }
        public static Limits defaults() { return new Limits(5_000, 1_000_000, 128, 65_536); }
    }

    public record Request(int schemaVersion, String profile, String source, String input, Limits limits) {
        public Request {
            require(schemaVersion == 1, "schemaVersion must be 1");
            require(PROFILE.equals(profile), "profile must be " + PROFILE);
            require(source != null && !source.isBlank() && source.length() <= MAX_SOURCE_CHARS,
                    "source must contain 1..131072 characters");
            require(input == null || input.length() <= MAX_INPUT_CHARS, "input must contain at most 65536 characters");
            input = input == null ? "" : input;
            limits = limits == null ? Limits.defaults() : limits;
        }
    }

    public enum Status { SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, INVALID_REQUEST, TIME_LIMIT, OUTPUT_LIMIT, WORKER_ERROR }
    public record Diagnostic(String severity, String code, String message, int line, int column) {}
    public record Response(int schemaVersion, String profile, String compilerVersion, String sourceSha256,
                           Status status, String stdout, String stderr, List<Diagnostic> diagnostics,
                           String message, int workerExitCode, long elapsedMs) {}
    private record WorkerResult(Status status, List<Diagnostic> diagnostics, String message) {}
    record Capture(String text) {}

    private record Invocation(List<SourceBundle.Source> sources, String input, Limits limits, boolean checkOnly) {
        private Invocation {
            sources = SourceBundle.validate(sources);
            require(input == null || input.length() <= MAX_INPUT_CHARS, "input must contain at most 65536 characters");
            input = input == null ? "" : input;
            limits = limits == null ? Limits.defaults() : limits;
        }
    }

    public static Request readRequest(Path file) throws IOException {
        return readJson(file, Request.class);
    }

    static <T> T readJson(Path file, Class<T> type) throws IOException {
        T value = JSON.readValue(readLimited(file, MAX_REQUEST_BYTES), type);
        if (value == null) throw new IOException("Expected a JSON object, not null");
        return value;
    }

    static String writeJson(Object value) throws IOException {
        return JSON.writeValueAsString(value);
    }

    private static byte[] readLimited(Path file, int maximum) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("Input exceeds " + maximum + " bytes");
            return bytes;
        }
    }

    public static Response execute(Request request, Path compilerJar) throws IOException, InterruptedException {
        java.util.Objects.requireNonNull(request, "request");
        return execute(new Invocation(List.of(new SourceBundle.Source("request.dhr", request.source())),
                request.input(), request.limits(), false), compilerJar, sha256(request.source()));
    }

    public static Response executeSources(List<SourceBundle.Source> sources, String input, Limits limits,
                                          boolean checkOnly, Path compilerJar) throws IOException, InterruptedException {
        Invocation invocation = new Invocation(sources, input, limits, checkOnly);
        return execute(invocation, compilerJar, SourceBundle.fingerprint(invocation.sources()));
    }

    private static Response execute(Invocation request, Path compilerJar, String sourceIdentity)
            throws IOException, InterruptedException {
        if (!Files.isRegularFile(compilerJar)) throw new IOException("Missing packaged compiler: " + compilerJar);
        long start = System.nanoTime();
        Path directory = Files.createTempDirectory("dhrlang-host-");
        Process process = null;
        ExecutorService io = Executors.newFixedThreadPool(2);
        try {
            Path inputFile = directory.resolve("request.json");
            Path outputFile = directory.resolve("response.json");
            Path stdinFile = directory.resolve("stdin.txt");
            byte[] invocation = JSON.writeValueAsBytes(request);
            if (invocation.length > MAX_REQUEST_BYTES) throw new IOException("Serialized source bundle exceeds request limit");
            Files.write(inputFile, invocation);
            Files.writeString(stdinFile, request.input(), StandardCharsets.UTF_8);
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
            Limits limits = request.limits();
            ProcessBuilder builder = new ProcessBuilder(java, "-Xms16m", "-Xmx" + limits.heapMb() + "m",
                    "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=128m", "-XX:+ExitOnOutOfMemoryError",
                    "-Dfile.encoding=UTF-8", "-Duser.home=" + directory, "-Djava.io.tmpdir=" + directory,
                    "-Ddhrlang.bytecode.untrusted=true", "-Ddhrlang.bytecode.maxCallDepth=256",
                    "-Ddhrlang.backend.maxSteps=" + limits.maxSteps(), "-Ddhrlang.host.restricted=true",
                    "-cp", compilerJar.toAbsolutePath().toString(), HostExecution.class.getName(),
                    inputFile.toString(), outputFile.toString());
            builder.directory(directory.toFile());
            builder.redirectInput(stdinFile.toFile());
            Map<String, String> environment = builder.environment();
            Map<String, String> permitted = workerEnvironment(environment);
            environment.clear();
            environment.putAll(permitted);
            process = builder.start();
            Process worker = process;
            AtomicInteger outputBytes = new AtomicInteger();
            AtomicBoolean truncated = new AtomicBoolean();
            AtomicBoolean terminationRequested = new AtomicBoolean();
            Future<Capture> stdout = io.submit(() -> capture(worker.getInputStream(), worker,
                    outputBytes, truncated, terminationRequested, limits.maxOutputBytes()));
            Future<Capture> stderr = io.submit(() -> capture(worker.getErrorStream(), worker,
                    outputBytes, truncated, terminationRequested, limits.maxOutputBytes()));
            boolean finished = worker.waitFor(limits.timeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminationRequested.set(true);
                worker.destroyForcibly();
                if (!worker.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Could not terminate timed-out worker");
            }
            Capture out = await(stdout);
            Capture err = await(stderr);
            Status status;
            WorkerResult result = null;
            String message;
            if (truncated.get()) {
                status = Status.OUTPUT_LIMIT;
                message = "Combined program stdout/stderr exceeded the output budget";
            } else if (!finished) {
                status = Status.TIME_LIMIT;
                message = "Worker exceeded the wall-clock budget (including compilation)";
            } else if (worker.exitValue() != 0 || !Files.isRegularFile(outputFile)) {
                status = Status.WORKER_ERROR;
                message = "Worker exited without a valid result; inspect bounded stderr";
            } else {
                result = JSON.readValue(readLimited(outputFile, MAX_REQUEST_BYTES), WorkerResult.class);
                status = result.status();
                message = result.message();
            }
            return new Response(1, PROFILE, version(), sourceIdentity, status, out.text(), err.text(),
                    result == null ? List.of() : result.diagnostics(), message, worker.exitValue(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            io.shutdownNow();
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IOException("Worker I/O threads did not terminate");
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    static Map<String, String> workerEnvironment(Map<String, String> parent) {
        return parent.containsKey("SystemRoot") ? Map.of("SystemRoot", parent.get("SystemRoot")) : Map.of();
    }

    static Capture capture(InputStream input, Process worker, AtomicInteger total,
                           AtomicBoolean truncated, AtomicBoolean terminationRequested, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (input) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                int previous = total.getAndAdd(count);
                int keep = Math.min(count, Math.max(0, limit - previous));
                bytes.write(buffer, 0, keep);
                if (previous + count > limit) {
                    truncated.set(true);
                    terminationRequested.set(true);
                    worker.destroyForcibly();
                    break;
                }
            }
        } catch (IOException failure) {
            // Process.destroyForcibly closes Linux pipes. The explicit failure
            // status (output/time limit) remains authoritative after cancellation.
            if (!terminationRequested.get()) throw failure;
        }
        // Do not expand a truncated UTF-8 character into a three-byte replacement
        // character after the byte budget has already been exhausted.
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE);
        return new Capture(decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString());
    }

    private static Capture await(Future<Capture> result) throws IOException, InterruptedException {
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            throw new IOException("Could not capture worker output", failure.getCause());
        } catch (TimeoutException failure) {
            throw new IOException("Worker output did not close", failure);
        }
    }

    /** Worker entry point. The normal user entry point is {@code Main host request.json}. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Worker expects request and response paths");
        Invocation request = readJson(Path.of(args[0]), Invocation.class);
        WorkerResult result = evaluate(request);
        JSON.writeValue(Path.of(args[1]).toFile(), result);
    }

    private static WorkerResult evaluate(Invocation request) {
        ErrorReporter reporter = new ErrorReporter();
        reporter.setColorEnabled(false);
        try {
            Program program = SourceBundle.parse(request.sources(), reporter);
            if (reporter.hasErrors()) return compilationFailure(reporter);
            if (program.getClasses().stream().anyMatch(c -> c.isContract())) {
                return new WorkerResult(Status.COMPILE_ERROR, List.of(),
                        "The host profile accepts JVM programs, not EVM contracts");
            }
            new TypeChecker(reporter).check(program);
            if (reporter.hasErrors()) return compilationFailure(reporter);
            var ir = new AstToIrLowerer(reporter).lower(program);
            if (reporter.hasErrors()) return compilationFailure(reporter);
            IrOptimizer.defaultPipeline().optimize(ir);
            byte[] code = new BytecodeWriter().write(ir);
            if (request.checkOnly()) {
                return new WorkerResult(Status.SUCCESS, diagnostics(reporter), "Source bundle compiled; no program code executed");
            }
            new BytecodeVM().execute(code);
            return new WorkerResult(Status.SUCCESS, diagnostics(reporter), "Program completed");
        } catch (DhrRuntimeException failure) {
            var location = failure.getLocation();
            return new WorkerResult(Status.RUNTIME_ERROR, List.of(new Diagnostic("ERROR",
                    failure.getCategory().name(), bounded(failure.getMessage()),
                    location == null ? 0 : location.getLine(), location == null ? 0 : location.getColumn())),
                    "Program execution failed");
        } catch (IllegalArgumentException failure) {
            return new WorkerResult(Status.WORKER_ERROR, diagnostics(reporter),
                    "Compiler/bytecode validation failed: " + bounded(failure.getMessage()));
        }
    }

    private static WorkerResult compilationFailure(ErrorReporter reporter) {
        return new WorkerResult(Status.COMPILE_ERROR, diagnostics(reporter), "Source did not compile");
    }

    private static List<Diagnostic> diagnostics(ErrorReporter reporter) {
        List<DhrError> errors = new ArrayList<>(reporter.getErrors());
        errors.addAll(reporter.getWarnings());
        return errors.stream().limit(50).map(error -> new Diagnostic(error.getType().name(),
                error.getCode() == null ? "" : error.getCode().name(), bounded(locatedMessage(error)),
                error.getLocation() == null ? 0 : error.getLocation().getLine(),
                error.getLocation() == null ? 0 : error.getLocation().getColumn())).toList();
    }

    private static String locatedMessage(DhrError error) {
        String filename = error.getLocation() == null ? null : error.getLocation().getFilename();
        return filename == null ? error.getMessage()
                : "[" + filename + "] " + error.getMessage();
    }

    public static int runCli(String[] args) {
        Response response;
        int exit;
        try {
            if (args.length != 2) throw new IllegalArgumentException("Usage: java -jar DhrLang.jar host <request.json>");
            Request request;
            try {
                request = readRequest(Path.of(args[1]));
            } catch (IOException failure) {
                throw new IllegalArgumentException("Invalid request: " + bounded(failure.getMessage()), failure);
            }
            Path jar = compilerJar();
            response = execute(request, jar);
            exit = response.status() == Status.SUCCESS ? 0 : 2;
        } catch (IllegalArgumentException failure) {
            response = failure(Status.INVALID_REQUEST, failure.getMessage());
            exit = 64;
        } catch (IOException | java.net.URISyntaxException failure) {
            response = failure(Status.WORKER_ERROR, failure.getMessage());
            exit = 2;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            response = failure(Status.WORKER_ERROR, "Host execution was interrupted");
            exit = 2;
        }
        try {
            System.out.println(JSON.writeValueAsString(response));
        } catch (IOException failure) {
            System.err.println("Could not serialize host result: " + failure.getMessage());
            return 2;
        }
        return exit;
    }

    static Response failure(Status status, String message) {
        return new Response(1, PROFILE, version(), "", status, "", "", List.of(), bounded(message), -1, 0);
    }

    static Path compilerJar() throws IOException, java.net.URISyntaxException {
        Path jar = Path.of(HostExecution.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(jar)) throw new IOException("Host execution requires the packaged compiler JAR");
        return jar;
    }

    private static String version() {
        String version = HostExecution.class.getPackage().getImplementationVersion();
        return version == null ? "development" : version;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static String bounded(String message) {
        if (message == null) return "";
        return message.length() <= 2048 ? message : message.substring(0, 2048) + "...";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
