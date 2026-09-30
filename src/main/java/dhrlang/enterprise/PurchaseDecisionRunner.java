package dhrlang.enterprise;

import dhrlang.host.HostExecution;
import dhrlang.host.SourceBundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static dhrlang.enterprise.PurchaseApproval.*;

/** The DhrLang program proposes a decision; it never receives store or authentication capabilities. */
public final class PurchaseDecisionRunner implements PurchaseWorkflow.DecisionEngine {
    private static final HostExecution.Limits LIMITS = new HostExecution.Limits(5000, 1_000_000, 128, 4096);
    private final Path compiler;
    private final List<SourceBundle.Source> sources;

    public record Evaluation(Decision decision, HostExecution.Response execution, String error) {
        public boolean successful() {
            return decision != null && execution.status() == HostExecution.Status.SUCCESS
                    && execution.stderr().isEmpty() && error.isEmpty();
        }
    }

    public PurchaseDecisionRunner(Path compiler, String source) {
        this.compiler = compiler;
        this.sources = SourceBundle.validate(List.of(new SourceBundle.Source("purchase-policy.dhr", source)));
    }

    public static String referenceSource() throws IOException {
        try (InputStream stream = PurchaseDecisionRunner.class.getResourceAsStream("/dhrlang/enterprise/purchase-policy.dhr")) {
            if (stream == null) throw new IOException("Packaged purchase policy is missing");
            return readSource(stream);
        }
    }

    public static String readSource(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Policy must be a regular UTF-8 source file");
        try (InputStream stream = Files.newInputStream(file)) {
            return readSource(stream);
        }
    }

    private static String readSource(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(HostExecution.MAX_REQUEST_BYTES + 1);
        if (bytes.length > HostExecution.MAX_REQUEST_BYTES) throw new IOException("Policy source is too large");
        String source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        SourceBundle.validate(List.of(new SourceBundle.Source("purchase-policy.dhr", source)));
        return source;
    }

    public static void writeReference(Path file) throws IOException {
        Files.writeString(file, referenceSource(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public static String input(Snapshot snapshot, Principal actor) {
        Request request = snapshot.request();
        Budget budget = snapshot.budget();
        return request.amountMinor() + "\n" + budget.availableMinor() + "\n" + actor.approvalLimitMinor() + "\n"
                + request.currency() + "\n" + budget.currency() + "\n" + actor.approvalCurrency() + "\n"
                + request.requester() + "\n" + actor.id() + "\n"
                + actor.mayApprove() + "\n" + actor.costCenters().contains(request.costCenter()) + "\n"
                + request.supplierActive() + "\n" + request.state().name() + "\n";
    }

    @Override
    public Evaluation evaluate(Snapshot snapshot, Principal actor) throws IOException, InterruptedException {
        var execution = HostExecution.executeSources(sources, input(snapshot, actor), LIMITS, false, compiler);
        if (execution.status() != HostExecution.Status.SUCCESS || !execution.stderr().isEmpty()) {
            return new Evaluation(null, execution, execution.status() == HostExecution.Status.SUCCESS
                    ? "Policy wrote to stderr" : "Policy execution failed: " + execution.status());
        }
        String output = execution.stdout().replace("\r\n", "\n");
        if (output.endsWith("\n")) output = output.substring(0, output.length() - 1);
        String[] lines = output.split("\n", -1);
        if (lines.length != 3) return new Evaluation(null, execution, "Policy must print exactly outcome, reason and remaining minor units");
        try {
            Decision decision = new Decision(Outcome.valueOf(lines[0]), Reason.valueOf(lines[1]), Long.parseLong(lines[2]));
            return new Evaluation(decision, execution, "");
        } catch (IllegalArgumentException failure) {
            return new Evaluation(null, execution, "Invalid policy output: " + failure.getMessage());
        }
    }
}
