package dhrlang.host;

import dhrlang.ast.ClassDecl;
import dhrlang.ast.InterfaceDecl;
import dhrlang.ast.Program;
import dhrlang.error.DhrError;
import dhrlang.error.ErrorReporter;
import dhrlang.error.SourceLocation;
import dhrlang.lexer.Lexer;
import dhrlang.parser.ParseException;
import dhrlang.parser.Parser;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Ordered source units share a namespace, but never a lexer or parser state. */
public final class SourceBundle {
    public static final int MAX_FILES = 32;
    public static final int MAX_SOURCE_CHARS = 128 * 1024;

    public record Source(String path, String content) {
        public Source {
            if (path == null || path.isBlank() || path.length() > 256) {
                throw new IllegalArgumentException("Source path must contain 1..256 characters");
            }
            if (content == null) throw new IllegalArgumentException("Source content must not be null");
        }
    }

    private SourceBundle() {}

    public static List<Source> validate(List<Source> sources) {
        if (sources == null || sources.isEmpty() || sources.size() > MAX_FILES) {
            throw new IllegalArgumentException("A project must contain 1..32 source files");
        }
        List<Source> snapshot = List.copyOf(sources);
        var names = new HashSet<String>();
        long length = 0;
        for (Source source : snapshot) {
            if (!names.add(source.path())) throw new IllegalArgumentException("Duplicate source path: " + source.path());
            length += source.content().length();
        }
        if (length == 0 || length > MAX_SOURCE_CHARS) {
            throw new IllegalArgumentException("Combined source must contain 1..131072 characters");
        }
        return snapshot;
    }

    public static Program parse(List<Source> sources, ErrorReporter reporter) {
        List<ClassDecl> classes = new ArrayList<>();
        List<InterfaceDecl> interfaces = new ArrayList<>();
        for (Source source : sources) {
            reporter.registerSource(source.path(), source.content());
            ErrorReporter local = new ErrorReporter(source.path(), source.content());
            try {
                var tokens = new Lexer(source.content(), local).scanTokens().stream()
                        .map(token -> token.inFile(source.path())).toList();
                if (!local.hasErrors()) {
                    Program unit = new Parser(tokens, local).parse();
                    classes.addAll(unit.getClasses());
                    interfaces.addAll(unit.getInterfaces());
                }
            } catch (ParseException failure) {
                if (!local.hasErrors()) local.error(1, failure.getMessage());
            }
            for (DhrError error : local.getErrors()) {
                reporter.error(locate(error, source.path()), error.getMessage(), error.getHint(), error.getCode());
            }
            for (DhrError warning : local.getWarnings()) {
                reporter.warning(locate(warning, source.path()), warning.getMessage(), warning.getHint(), warning.getCode());
            }
        }
        return new Program(classes, interfaces);
    }

    private static SourceLocation locate(DhrError error, String path) {
        SourceLocation location = error.getLocation();
        return location == null ? new SourceLocation(path, 0, 0)
                : new SourceLocation(path, location.getLine(), location.getColumn(),
                location.getStartOffset(), location.getEndOffset());
    }

    public static String fingerprint(List<Source> sources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("dhrlang-source-bundle-v1".getBytes(StandardCharsets.UTF_8));
            for (Source source : sources) {
                update(digest, source.path());
                update(digest, source.content());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
