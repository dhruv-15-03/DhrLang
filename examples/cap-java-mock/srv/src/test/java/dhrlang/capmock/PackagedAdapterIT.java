package dhrlang.capmock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PackagedAdapterIT {
    @TempDir Path directory;

    @Test
    void executableBootJarUsesItsNestedApiAndServesOnlyLoopback() throws Exception {
        Running application = launch(worker());
        try {
            int port = awaitPort(application);
            URI root = URI.create("http://127.0.0.1:" + port + "/odata/v4/purchase-lab");
            String authorization = "Basic " + Base64.getEncoder().encodeToString(
                    "bob-a:local-test-only".getBytes(StandardCharsets.UTF_8));
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                var anonymous = client.send(HttpRequest.newBuilder(URI.create(root + "/$metadata"))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(401, anonymous.statusCode());
                var metadata = client.send(HttpRequest.newBuilder(URI.create(root + "/$metadata"))
                        .timeout(Duration.ofSeconds(15)).header("Authorization", authorization).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, metadata.statusCode(), metadata.body());
                assertTrue(metadata.body().contains("Action Name=\"approve\""));
                var approved = client.send(HttpRequest.newBuilder(URI.create(root + "/approve"))
                        .timeout(Duration.ofSeconds(20)).header("Authorization", authorization)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"command":{"eventId":"packaged","requestId":"purchase-001","requestVersion":"0","budgetVersion":"0"}}
                                """)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, approved.statusCode(), approved.body());
                var result = new ObjectMapper().readTree(approved.body());
                assertEquals(LocalCapMockApplication.PROFILE, result.path("profile").asText());
                assertEquals("APPLIED", result.path("status").asText());
                assertEquals("training-a", result.path("receipt").path("tenant").asText());
                assertEquals(37500, result.path("receipt").path("budgetMinor").asLong());
                assertTrue(result.path("receipt").path("requestVersion").isTextual());
                var snapshotReply = client.send(HttpRequest.newBuilder(
                                URI.create(root + "/snapshot(requestId='precise-version')"))
                        .timeout(Duration.ofSeconds(15)).header("Authorization", authorization).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, snapshotReply.statusCode(), snapshotReply.body());
                var snapshot = new ObjectMapper().readTree(snapshotReply.body());
                assertTrue(snapshot.path("requestVersion").isTextual());
                assertTrue(snapshot.path("budgetVersion").isTextual());
                assertEquals("9007199254740993", snapshot.path("requestVersion").textValue());
                String reviewer = "Basic " + Base64.getEncoder().encodeToString(
                        "reviewer-a:local-test-only".getBytes(StandardCharsets.UTF_8));
                var review = sendRevisionCommand(client, root, reviewer, "exact-review",
                        snapshot.path("requestVersion").textValue(), snapshot.path("budgetVersion").textValue());
                assertEquals("NEEDS_REVIEW", review.path("receipt").path("outcome").asText());
                assertTrue(review.path("receipt").path("requestVersion").isTextual());
                assertEquals("9007199254740994", review.path("receipt").path("requestVersion").textValue());
                var finalApproval = sendRevisionCommand(client, root, authorization, "exact-approval",
                        review.path("receipt").path("requestVersion").textValue(),
                        review.path("receipt").path("budgetVersion").textValue());
                assertTrue(finalApproval.path("receipt").path("requestVersion").isTextual());
                assertTrue(finalApproval.path("receipt").path("budgetVersion").isTextual());
                assertEquals("9007199254740995", finalApproval.path("receipt").path("requestVersion").textValue());
                assertEquals("9007199254740994", finalApproval.path("receipt").path("budgetVersion").textValue());
            }
        } finally {
            stop(application);
        }
    }

    @Test
    void packagedMainRejectsNonLoopbackConfiguration() throws Exception {
        Running application = launch(worker(), "--server.address=0.0.0.0");
        try {
            assertTrue(application.process().waitFor(40, TimeUnit.SECONDS), log(application));
            assertNotEquals(0, application.process().exitValue(), log(application));
            assertTrue(log(application).contains("must bind to loopback"), log(application));
        } finally {
            stop(application);
        }
    }

    @Test
    void packagedMainRejectsSameVersionWithDifferentCompilerBytes() throws Exception {
        Path altered = directory.resolve("altered-worker.jar");
        boolean changed = false;
        try (var input = new JarInputStream(Files.newInputStream(worker()));
             var output = new JarOutputStream(Files.newOutputStream(altered), input.getManifest())) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                output.putNextEntry(new JarEntry(entry.getName()));
                if ("dhrlang/enterprise/PurchaseApproval.class".equals(entry.getName())) {
                    byte[] bytes = input.readAllBytes();
                    bytes[bytes.length - 1] ^= 1;
                    output.write(bytes);
                    changed = true;
                } else {
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
        assertTrue(changed, "The mismatch test must actually change an API class");
        Running application = launch(altered);
        try {
            assertTrue(application.process().waitFor(60, TimeUnit.SECONDS), log(application));
            assertNotEquals(0, application.process().exitValue(), log(application));
            assertTrue(log(application).contains("API/worker compiler revisions differ"), log(application));
        } finally {
            stop(application);
        }
    }

    private record Running(Process process, Path output) {}

    private static com.fasterxml.jackson.databind.JsonNode sendRevisionCommand(
            HttpClient client, URI root, String authorization, String event, String requestVersion, String budgetVersion)
            throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(root + "/approve"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"command\":{\"eventId\":\"" + event
                        + "\",\"requestId\":\"precise-version\",\"requestVersion\":\"" + requestVersion
                        + "\",\"budgetVersion\":\"" + budgetVersion + "\"}}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return new ObjectMapper().readTree(response.body());
    }

    private Running launch(Path worker, String... overrides) throws IOException {
        Path packaged = Path.of(System.getProperty("adapter.jar")).toAbsolutePath();
        assertTrue(Files.isRegularFile(packaged), "The actual repackaged Boot artifact is required");
        Path output = Files.createTempFile(directory, "server-", ".log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xms32m", "-Xmx256m", "-XX:MaxMetaspaceSize=192m", "-XX:+UseSerialGC",
                "-Dfile.encoding=UTF-8", "-Duser.home=" + directory, "-Djava.io.tmpdir=" + directory,
                "-jar", packaged.toString(), "--dhrlang.compiler-jar=" + worker.toAbsolutePath(),
                "--spring.profiles.active=local-cap-mock", "--server.address=127.0.0.1", "--server.port=0"));
        command.addAll(List.of(overrides));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        String systemRoot = builder.environment().get("SystemRoot");
        builder.environment().clear();
        if (systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
        Process process = builder.start();
        process.getOutputStream().close();
        return new Running(process, output);
    }

    private static int awaitPort(Running application) throws Exception {
        Pattern started = Pattern.compile("Tomcat started on port (\\d+)");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            var match = started.matcher(log(application));
            if (match.find()) return Integer.parseInt(match.group(1));
            if (!application.process().isAlive()) fail("Packaged startup failed:\n" + log(application));
            Thread.sleep(100);
        }
        fail("Packaged server did not become ready:\n" + log(application));
        return -1;
    }

    private static Path worker() {
        return Path.of(System.getProperty("dhrlang.compiler-jar")).toAbsolutePath().normalize();
    }

    private static String log(Running application) throws IOException {
        return Files.readString(application.output(), StandardCharsets.UTF_8);
    }

    private static void stop(Running application) throws InterruptedException {
        if (application.process().isAlive()) {
            application.process().destroy();
            if (!application.process().waitFor(5, TimeUnit.SECONDS)) {
                application.process().destroyForcibly();
                assertTrue(application.process().waitFor(5, TimeUnit.SECONDS), "Owned test server did not stop");
            }
        }
    }
}
