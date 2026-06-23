package dhrlang.deploy;

import dhrlang.deploy.BlockchainCLI.BlockchainOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the {@code dhrlang contract stdlib <list|show|new>} CLI surface:
 * argument parsing plus the end-to-end routing (which scaffolds files and prints
 * the catalog). The end-to-end cases spawn a fresh JVM on the test classpath so
 * they always exercise newly-compiled classes (never a stale assembled jar).
 */
public class BlockchainStdlibCliTest {

    private static final String JAVA =
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

    // ── parseArgs ─────────────────────────────────────────────────────────

    @Test
    void parsesListAction() {
        BlockchainOptions opts = BlockchainCLI.parseArgs(
                new String[]{"contract", "stdlib", "list"}, 1);
        assertEquals("stdlib", opts.subcommand);
        assertEquals("list", opts.stdlibAction);
        assertNull(opts.stdlibName);
    }

    @Test
    void parsesShowWithName() {
        BlockchainOptions opts = BlockchainCLI.parseArgs(
                new String[]{"contract", "stdlib", "show", "ERC20"}, 1);
        assertEquals("stdlib", opts.subcommand);
        assertEquals("show", opts.stdlibAction);
        assertEquals("ERC20", opts.stdlibName);
    }

    @Test
    void parsesNewWithCustomNameAndOutput() {
        BlockchainOptions opts = BlockchainCLI.parseArgs(
                new String[]{"contract", "stdlib", "new", "Ownable",
                        "--name=MyToken", "--output=out/dir"}, 1);
        assertEquals("new", opts.stdlibAction);
        assertEquals("Ownable", opts.stdlibName);
        assertEquals("MyToken", opts.stdlibCustomName);
        assertEquals("out/dir", opts.outputDir);
    }

    // ── end-to-end routing ────────────────────────────────────────────────

    @Test
    void listPrintsCatalogAndExitsZero() throws Exception {
        Result r = run("contract", "stdlib", "list");
        assertEquals(0, r.exit, "stdlib list should exit 0. Got: " + r.out);
        assertTrue(r.out.contains("Ownable") && r.out.contains("ERC20"),
                "catalog should list known contracts. Got: " + r.out);
    }

    @Test
    void showPrintsSourceForKnownContract() throws Exception {
        Result r = run("contract", "stdlib", "show", "ERC20");
        assertEquals(0, r.exit, "stdlib show ERC20 should exit 0. Got: " + r.out);
        assertTrue(r.out.contains("@contract") && r.out.contains("class ERC20"),
                "show should print the template source. Got: " + r.out);
    }

    @Test
    void showUnknownContractExitsNonZero() throws Exception {
        Result r = run("contract", "stdlib", "show", "DoesNotExist");
        assertNotEquals(0, r.exit, "unknown template should fail. Got: " + r.out);
    }

    @Test
    void newScaffoldsFileWithCustomName(@TempDir Path tmp) throws Exception {
        Result r = run("contract", "stdlib", "new", "Ownable",
                "--name=MyToken", "--output=" + tmp.toString());
        assertEquals(0, r.exit, "stdlib new should exit 0. Got: " + r.out);

        Path scaffold = tmp.resolve("MyToken.dhr");
        assertTrue(Files.exists(scaffold), "expected scaffolded file MyToken.dhr");
        String src = Files.readString(scaffold);
        assertTrue(src.contains("class MyToken"),
                "scaffold should rename the contract class to the custom name");
        assertFalse(src.contains("class Ownable"),
                "original template class name should be replaced");
    }

    @Test
    void newWithoutCustomNameUsesTemplateName(@TempDir Path tmp) throws Exception {
        Result r = run("contract", "stdlib", "new", "Pausable",
                "--output=" + tmp.toString());
        assertEquals(0, r.exit, "stdlib new should exit 0. Got: " + r.out);
        assertTrue(Files.exists(tmp.resolve("Pausable.dhr")),
                "expected scaffolded file Pausable.dhr");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private record Result(int exit, String out) {}

    private Result run(String... toolArgs) throws IOException, InterruptedException {
        String cp = System.getProperty("java.class.path");
        String[] full = new String[4 + toolArgs.length];
        full[0] = JAVA;
        full[1] = "-cp";
        full[2] = cp;
        full[3] = "dhrlang.Main";
        System.arraycopy(toolArgs, 0, full, 4, toolArgs.length);

        ProcessBuilder pb = new ProcessBuilder(full);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(baos);
        }
        int exit = p.waitFor();
        return new Result(exit, baos.toString(StandardCharsets.UTF_8));
    }
}
