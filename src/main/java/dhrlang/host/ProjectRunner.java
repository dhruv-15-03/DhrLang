package dhrlang.host;

import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Loads an explicit local project into the bounded worker; no imports, globs or package resolution. */
public final class ProjectRunner {
    public record Manifest(int schemaVersion, String compilerVersion, String profile,
                           List<String> sources, String input, HostExecution.Limits limits) {
        public Manifest {
            require(schemaVersion == 1, "schemaVersion must be 1");
            require(compilerVersion != null && compilerVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?"),
                    "compilerVersion must be an exact semantic version");
            require(HostExecution.PROFILE.equals(profile), "profile must be " + HostExecution.PROFILE);
            require(sources != null && !sources.isEmpty() && sources.size() <= SourceBundle.MAX_FILES,
                    "sources must list 1..32 files in initialization order");
            for (String source : sources) require(source != null && !source.isBlank(), "Source paths must not be blank");
            sources = List.copyOf(sources);
            require(input == null || input.length() <= 65536, "input must contain at most 65536 characters");
            input = input == null ? "" : input;
            limits = limits == null ? HostExecution.Limits.defaults() : limits;
        }
    }

    public record Project(Manifest manifest, List<SourceBundle.Source> sources) {
        public Project { sources = SourceBundle.validate(sources); }
    }

    public record Result(int schemaVersion, String operation, List<String> sources, HostExecution.Response execution) {}

    private ProjectRunner() {}

    public static Project load(Path manifestFile) throws IOException {
        Path manifestPath = manifestFile.toAbsolutePath().normalize().toRealPath();
        require(Files.isRegularFile(manifestPath), "Project manifest must be a regular file");
        Manifest manifest = HostExecution.readJson(manifestPath, Manifest.class);
        Path root = manifestPath.getParent();
        var physicalFiles = new HashSet<Path>();
        var sourceFiles = new ArrayList<SourceBundle.Source>();
        int remaining = SourceBundle.MAX_SOURCE_CHARS;
        for (String name : manifest.sources()) {
            require(name.length() <= 256 && !name.contains(":")
                            && name.chars().noneMatch(Character::isISOControl),
                    "Source path is not a portable relative path: " + name);
            Path relative = Path.of(name.replace('\\', File.separatorChar).replace('/', File.separatorChar));
            require(!relative.isAbsolute(), "Absolute source paths are not allowed: " + name);
            for (Path segment : relative) {
                require(!"..".equals(segment.toString()), "Parent-directory source paths are not allowed: " + name);
            }
            Path candidate = root.resolve(relative).normalize();
            require(candidate.startsWith(root), "Source escapes project root: " + name);
            Path real = candidate.toRealPath();
            require(real.startsWith(root), "Source symlink escapes project root: " + name);
            require(Files.isRegularFile(real) && name.toLowerCase(java.util.Locale.ROOT).endsWith(".dhr"),
                    "Source must be a regular .dhr file: " + name);
            require(physicalFiles.add(real), "Duplicate source file: " + name);
            String content;
            try (var stream = Files.newInputStream(real)) {
                byte[] bytes = stream.readNBytes(SourceBundle.MAX_SOURCE_CHARS * 4 + 1);
                require(bytes.length <= SourceBundle.MAX_SOURCE_CHARS * 4, "Source file exceeds project limit: " + name);
                try {
                    content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                } catch (CharacterCodingException failure) {
                    throw new IOException("Source is not valid UTF-8: " + name, failure);
                }
            }
            remaining -= content.length();
            require(remaining >= 0, "Combined source exceeds 131072 characters");
            String logicalName = relative.normalize().toString().replace('\\', '/');
            sourceFiles.add(new SourceBundle.Source(logicalName, content));
        }
        return new Project(manifest, sourceFiles);
    }

    public static Result execute(Project project, boolean checkOnly, Path compilerJar)
            throws IOException, InterruptedException {
        String actual = HostExecution.compilerVersion(compilerJar);
        require(project.manifest().compilerVersion().equals(actual),
                "Project requires compiler " + project.manifest().compilerVersion() + ", found " + actual);
        HostExecution.Response response = HostExecution.executeSources(project.sources(),
                project.manifest().input(), project.manifest().limits(), checkOnly, compilerJar);
        return new Result(1, checkOnly ? "check" : "run", project.sources().stream()
                .map(SourceBundle.Source::path).toList(), response);
    }

    public static int runCli(String[] args) {
        String operation = args.length > 1 ? args[1] : "";
        Result result;
        int exit;
        try {
            require(args.length == 3 && ("check".equals(operation) || "run".equals(operation)),
                    "Usage: java -jar DhrLang.jar project <check|run> <dhrlang.json>");
            Project project;
            try {
                project = load(Path.of(args[2]));
            } catch (IOException failure) {
                throw new IllegalArgumentException("Invalid project: " + failure.getMessage(), failure);
            }
            result = execute(project, "check".equals(operation), HostExecution.compilerJar());
            exit = result.execution().status() == HostExecution.Status.SUCCESS ? 0 : 2;
        } catch (IllegalArgumentException failure) {
            result = new Result(1, operation, List.of(), HostExecution.failure(HostExecution.Status.INVALID_REQUEST, failure.getMessage()));
            exit = 64;
        } catch (IOException | java.net.URISyntaxException failure) {
            result = new Result(1, operation, List.of(), HostExecution.failure(HostExecution.Status.WORKER_ERROR, failure.getMessage()));
            exit = 2;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            result = new Result(1, operation, List.of(), HostExecution.failure(HostExecution.Status.WORKER_ERROR, "Project execution interrupted"));
            exit = 2;
        }
        try {
            System.out.println(HostExecution.writeJson(result));
        } catch (IOException failure) {
            System.err.println("Could not serialize project result: " + failure.getMessage());
            return 2;
        }
        return exit;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
