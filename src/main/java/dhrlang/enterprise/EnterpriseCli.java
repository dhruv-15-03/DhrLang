package dhrlang.enterprise;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dhrlang.host.HostExecution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dhrlang.enterprise.PurchaseApproval.*;

/** Offline fixture verification and a mock transaction demonstration; no SAP connectivity. */
public final class EnterpriseCli {
    private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    public record Fixture(String id, Snapshot snapshot, Principal actor, Decision expected) {
        public Fixture {
            if (!identifier(id) || snapshot == null || actor == null || expected == null) {
                throw new IllegalArgumentException("Incomplete enterprise fixture");
            }
        }
    }
    public record Fixtures(int schemaVersion, List<Fixture> cases) {
        public Fixtures {
            if (schemaVersion != 1 || cases == null || cases.isEmpty() || cases.size() > 32
                    || cases.stream().anyMatch(java.util.Objects::isNull)
                    || cases.stream().map(Fixture::id).distinct().count() != cases.size()) {
                throw new IllegalArgumentException("Invalid enterprise fixture catalog");
            }
            cases = List.copyOf(cases);
        }
    }
    public record CaseResult(String id, boolean passed, Decision expected, Decision reference,
                             PurchaseDecisionRunner.Evaluation dhrlang) {}
    public record Verification(int schemaVersion, boolean mockOnly, int passed, int total, List<CaseResult> cases) {}
    public record Demo(int schemaVersion, boolean mockOnly, boolean passed, List<PurchaseWorkflow.Result> steps,
                       List<PurchaseWorkflow.Receipt> audit, long remainingBudgetMinor) {}

    private EnterpriseCli() {}

    public static Fixtures fixtures() throws IOException {
        try (var stream = EnterpriseCli.class.getResourceAsStream("/dhrlang/enterprise/purchase-cases.json")) {
            if (stream == null) throw new IOException("Packaged purchase fixtures are missing");
            return readFixtures(stream);
        }
    }

    static Fixtures readFixtures(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(256 * 1024 + 1);
        if (bytes.length > 256 * 1024) throw new IOException("Enterprise fixture catalog is too large");
        Fixtures fixtures = JSON.readValue(bytes, Fixtures.class);
        if (fixtures == null) throw new IOException("Enterprise fixture catalog must be an object");
        return fixtures;
    }

    public static Verification verify(PurchaseWorkflow.DecisionEngine engine) throws IOException, InterruptedException {
        var results = new ArrayList<CaseResult>();
        int passed = 0;
        for (Fixture fixture : fixtures().cases()) {
            Decision reference = evaluate(fixture.snapshot(), fixture.actor());
            var execution = engine.evaluate(fixture.snapshot(), fixture.actor());
            boolean matched = fixture.expected().equals(reference) && execution.successful()
                    && fixture.expected().equals(execution.decision());
            if (matched) passed++;
            results.add(new CaseResult(fixture.id(), matched, fixture.expected(), reference, execution));
        }
        return new Verification(1, true, passed, results.size(), List.copyOf(results));
    }

    public static Demo demo(PurchaseWorkflow.DecisionEngine engine) throws IOException, InterruptedException {
        Fixture fixture = fixtures().cases().stream().filter(item -> "within-policy".equals(item.id())).findFirst()
                .orElseThrow(() -> new IOException("Missing within-policy demonstration fixture"));
        Request first = fixture.snapshot().request();
        Budget budget = fixture.snapshot().budget();
        var second = new Request("purchase-002", "casey", first.supplier(), first.costCenter(), first.currency(),
                1000, true, State.SUBMITTED, 0);
        long expectedReservation = Math.addExact(first.amountMinor(), second.amountMinor());
        if (expectedReservation < 0 || expectedReservation > MAX_MINOR_UNITS || expectedReservation > budget.availableMinor()) {
            throw new IOException("Demonstration totals exceed the bounded synthetic budget");
        }
        long expectedRemaining = Math.subtractExact(budget.availableMinor(), expectedReservation);
        var store = new PurchaseWorkflow.Store(fixture.actor().tenant(), List.of(first, second), List.of(budget));
        var workflow = new PurchaseWorkflow(store, engine);
        var firstEvent = new PurchaseWorkflow.Command("approve-001", first.id(), first.version(), budget.version());
        var steps = new ArrayList<PurchaseWorkflow.Result>();
        steps.add(workflow.approve(firstEvent, fixture.actor()));
        steps.add(workflow.approve(firstEvent, fixture.actor()));
        var secondEvent = new PurchaseWorkflow.Command("lost-ack-002", second.id(), second.version(), budget.version() + 1);
        store.failNextCommit(PurchaseWorkflow.Fault.AFTER_COMMIT);
        steps.add(workflow.approve(secondEvent, fixture.actor()));
        steps.add(workflow.approve(secondEvent, fixture.actor()));
        long remaining = store.snapshot(first.id()).budget().availableMinor();
        boolean passed = steps.get(0).status() == PurchaseWorkflow.Status.APPLIED
                && steps.get(1).status() == PurchaseWorkflow.Status.REPLAYED
                && steps.get(2).status() == PurchaseWorkflow.Status.RECONCILIATION_REQUIRED
                && steps.get(3).status() == PurchaseWorkflow.Status.REPLAYED
                && store.audit().size() == 2 && remaining == expectedRemaining;
        return new Demo(1, true, passed, List.copyOf(steps), store.audit(), remaining);
    }

    public static int runCli(String[] args) {
        try {
            require(args.length >= 2, usage());
            if ("start".equals(args[1])) {
                require(args.length == 3, usage());
                PurchaseDecisionRunner.writeReference(Path.of(args[2]));
                System.out.println("Created the working reference policy at " + args[2] + "; existing files are never overwritten.");
                System.out.println("Extend a rule, rerun the shared fixtures, and explain which authority remains in the host.");
                return 0;
            }
            if ("cases".equals(args[1])) {
                require(args.length == 2, usage());
                System.out.println(JSON.writeValueAsString(fixtures()));
                return 0;
            }
            boolean json = "--json".equals(args[args.length - 1]);
            int positional = json ? args.length - 1 : args.length;
            if ("verify".equals(args[1])) {
                require(positional == 2 || (positional == 3 && !args[2].startsWith("--")), usage());
                String source = positional == 3 ? PurchaseDecisionRunner.readSource(Path.of(args[2]))
                        : PurchaseDecisionRunner.referenceSource();
                var report = verify(new PurchaseDecisionRunner(compiler(), source));
                if (json) System.out.println(JSON.writeValueAsString(report));
                else {
                    System.out.println(report.passed() + "/" + report.total() + " synthetic cases match the Java reference and expected fixtures");
                    for (CaseResult result : report.cases()) {
                        System.out.println((result.passed() ? "PASS " : "FAIL ") + result.id()
                                + ": expected " + result.expected() + "; DhrLang " + result.dhrlang().decision());
                        if (!result.passed()) {
                            System.out.println("  " + result.dhrlang().error());
                            System.out.println("  Worker: " + result.dhrlang().execution().message());
                            System.out.println("  Stdout: " + JSON.writeValueAsString(result.dhrlang().execution().stdout()));
                            System.out.println("  Stderr: " + JSON.writeValueAsString(result.dhrlang().execution().stderr()));
                            for (var diagnostic : result.dhrlang().execution().diagnostics()) System.out.println("  " + diagnostic.message());
                        }
                    }
                    System.out.println("Mock-only evidence; no SAP request or persistent business update was made.");
                }
                boolean infrastructureFailure = report.cases().stream().anyMatch(result ->
                        result.dhrlang().execution().status() == HostExecution.Status.WORKER_ERROR);
                return infrastructureFailure ? 2 : report.passed() == report.total() ? 0 : 1;
            }
            if ("demo".equals(args[1])) {
                require(positional == 2, usage());
                Demo report = demo(new PurchaseDecisionRunner(compiler(), PurchaseDecisionRunner.referenceSource()));
                if (json) System.out.println(JSON.writeValueAsString(report));
                else {
                    System.out.println("IN-MEMORY DEMO ONLY: approval, replay, lost acknowledgement, reconciliation.");
                    for (var step : report.steps()) System.out.println(step.status() + ": " + step.message());
                    System.out.println("Remaining synthetic budget: " + report.remainingBudgetMinor());
                    System.out.println(report.passed() ? "Expected simulation outcomes confirmed." : "Simulation did not match expected outcomes.");
                }
                return report.passed() ? 0 : 1;
            }
            throw new IllegalArgumentException(usage());
        } catch (IllegalArgumentException failure) {
            System.err.println("Enterprise command rejected: " + failure.getMessage());
            return 64;
        } catch (IOException | java.net.URISyntaxException failure) {
            System.err.println("Enterprise command failed: " + failure.getMessage());
            return 2;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            System.err.println("Enterprise command interrupted");
            return 2;
        }
    }

    private static Path compiler() throws IOException, java.net.URISyntaxException {
        Path jar = Path.of(EnterpriseCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(jar)) throw new IOException("Enterprise execution requires the packaged compiler; build stageCompiler");
        return jar;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static String usage() {
        return "Usage: learn enterprise cases | start <new-policy.dhr> | verify [policy.dhr] [--json] | demo [--json]";
    }
}
