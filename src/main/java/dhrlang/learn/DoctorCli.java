package dhrlang.learn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Local installation checks, not a runtime qualification or an OS sandbox. */
public final class DoctorCli {
    public record Check(String id, boolean passed, String detail) {}
    public record Report(String compilerVersion, List<Check> checks) {
        public Report { checks = List.copyOf(checks); }
        public boolean ready() { return checks.stream().allMatch(Check::passed); }
    }

    private DoctorCli() {}

    public static Report inspect(Path compiler) throws IOException {
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("java", Runtime.version().feature() >= 17,
                "Java " + Runtime.version() + "; requires Java 17+"));
        String version;
        try (JarFile jar = new JarFile(compiler.toFile())) {
            var manifest = jar.getManifest();
            version = manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
            String main = manifest == null ? null : manifest.getMainAttributes().getValue("Main-Class");
            checks.add(new Check("manifest", version != null && !version.isBlank() && "dhrlang.Main".equals(main),
                    "Implementation-Version=" + version + ", Main-Class=" + main));
            for (String entry : List.of(
                    "dhrlang/Main.class", "dhrlang/host/HostExecution.class", "dhrlang/host/ProjectRunner.class",
                    "dhrlang/learn/LearnCli.class", "dhrlang/lsp/DhrLangLspServer.class",
                    "org/bouncycastle/crypto/Digest.class", "com/fasterxml/jackson/databind/ObjectMapper.class",
                    "org/apache/commons/lang3/StringUtils.class", "dhrlang/learn/exercises.json")) {
                boolean present = jar.getEntry(entry) != null;
                checks.add(new Check(entry, present, present ? "Present in the standalone JAR"
                        : "Missing packaged component; rebuild current main with stageCompiler"));
            }
        }
        Path probe = Files.createTempFile("dhrlang-doctor-", ".tmp");
        try {
            Files.writeString(probe, "temporary storage check");
            checks.add(new Check("temporary-directory", true, "Temporary files can be created and written"));
        } finally {
            Files.delete(probe);
        }
        return new Report(version == null || version.isBlank() ? "unknown" : version, checks);
    }

    public static int runCli(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar DhrLang.jar doctor");
            return 64;
        }
        try {
            Path compiler = Path.of(DoctorCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(compiler)) {
                System.err.println("Doctor requires the packaged compiler JAR; build stageCompiler first.");
                return 2;
            }
            Report report = inspect(compiler);
            for (Check check : report.checks()) {
                System.out.println((check.passed() ? "PASS " : "FAIL ") + check.id() + ": " + check.detail());
            }
            System.out.println(report.ready()
                    ? "Installation checks passed. Run learn list to begin."
                    : "Installation is incomplete. Rebuild current main with stageCompiler.");
            System.out.println("These checks do not certify program correctness or provide an OS sandbox.");
            return report.ready() ? 0 : 1;
        } catch (IOException | java.net.URISyntaxException failure) {
            System.err.println("Installation check failed: " + failure.getMessage());
            return 2;
        }
    }
}
