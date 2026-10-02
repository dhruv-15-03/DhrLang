package dhrlang.capmock;

import dhrlang.enterprise.PurchaseApproval;

import java.io.IOException;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.jar.JarFile;

final class CompilerArtifacts {
    private CompilerArtifacts() {}

    static void requireMatchingCompiler(Path worker) throws IOException {
        var resource = PurchaseApproval.class.getResource("PurchaseApproval.class");
        if (resource == null || !Files.isRegularFile(worker)) {
            throw new IOException("Build/install the local source API and staged standalone compiler first");
        }
        var connection = resource.openConnection();
        connection.setUseCaches(false);
        if (!(connection instanceof JarURLConnection archive)) {
            throw new IOException("The local source API must be loaded from a JAR resource");
        }
        // JarURLConnection also supports Boot's nested BOOT-INF/lib archives without extraction.
        try (JarFile apiJar = archive.getJarFile(); JarFile workerJar = new JarFile(worker.toFile())) {
            String apiVersion = apiJar.getManifest() == null ? null
                    : apiJar.getManifest().getMainAttributes().getValue("Implementation-Version");
            String workerVersion = workerJar.getManifest() == null ? null
                    : workerJar.getManifest().getMainAttributes().getValue("Implementation-Version");
            if (apiVersion == null || !apiVersion.equals(workerVersion)) {
                throw new IOException("API/worker compiler versions differ; rebuild both artifacts");
            }
            var classes = apiJar.stream().filter(entry -> entry.getName().startsWith("dhrlang/")
                    && entry.getName().endsWith(".class")).toList();
            if (classes.isEmpty()) throw new IOException("No DhrLang API classes found");
            Set<String> names = classes.stream().map(java.util.jar.JarEntry::getName).collect(Collectors.toSet());
            var workerClasses = workerJar.stream().filter(entry -> entry.getName().startsWith("dhrlang/")
                    && entry.getName().endsWith(".class")).toList();
            Set<String> workerNames = workerClasses.stream().map(java.util.jar.JarEntry::getName).collect(Collectors.toSet());
            if (names.size() != classes.size() || workerNames.size() != workerClasses.size() || !names.equals(workerNames)) {
                throw new IOException("API/worker class sets differ; rebuild both artifacts");
            }
            for (var entry : classes) {
                var counterpart = workerJar.getJarEntry(entry.getName());
                if (entry.getSize() < 0 || entry.getSize() > 4 * 1024 * 1024 || counterpart.getSize() != entry.getSize()) {
                    throw new IOException("API/worker class sizes differ or exceed the verification limit");
                }
                try (var left = apiJar.getInputStream(entry); var right = workerJar.getInputStream(counterpart)) {
                    byte[] expected = left.readNBytes(4 * 1024 * 1024 + 1);
                    byte[] actual = right.readNBytes(4 * 1024 * 1024 + 1);
                    if (expected.length != entry.getSize() || !Arrays.equals(expected, actual)) {
                        throw new IOException("API/worker compiler revisions differ; rebuild both artifacts");
                    }
                }
            }
        }
    }
}
