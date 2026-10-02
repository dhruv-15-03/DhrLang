package dhrlang.module;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Resolves import statements by loading, parsing, and merging imported files.
 * Prevents circular imports via tracking visited paths.
 */
public final class ImportResolver {

    private final Path basePath;
    private final ErrorReporter errorReporter;
    private final Set<String> resolvedPaths = new HashSet<>();

    public ImportResolver(Path basePath, ErrorReporter errorReporter) {
        this.basePath = basePath;
        this.errorReporter = errorReporter;
    }

    /**
     * Resolve all imports in a program, merging imported classes/interfaces/enums.
     * Returns a new Program with all imports resolved.
     */
    public Program resolveImports(Program program) {
        List<ClassDecl> allClasses = new ArrayList<>(program.getClasses());
        List<InterfaceDecl> allInterfaces = new ArrayList<>(program.getInterfaces());
        List<EnumDecl> allEnums = new ArrayList<>(program.getEnums());

        for (ImportStmt imp : program.getImports()) {
            resolveImport(imp, allClasses, allInterfaces, allEnums);
        }

        return new Program(allClasses, allInterfaces, List.of(), allEnums);
    }

    private void resolveImport(ImportStmt imp, List<ClassDecl> classes,
                                List<InterfaceDecl> interfaces, List<EnumDecl> enums) {
        String importPath = imp.getPath();
        Path resolved = basePath.resolve(importPath).normalize();
        String canonicalPath = resolved.toString();

        if (resolvedPaths.contains(canonicalPath)) {
            return; // already imported — skip circular
        }
        resolvedPaths.add(canonicalPath);

        if (!Files.exists(resolved)) {
            if (errorReporter != null) {
                errorReporter.error(imp.getSourceLocation(),
                        "Cannot find imported file: " + importPath,
                        "Check the file path is relative to the current file's directory.");
            }
            return;
        }

        try {
            String source = Files.readString(resolved);
            ErrorReporter importReporter = new ErrorReporter(resolved.getFileName().toString(), source);
            Lexer lexer = new Lexer(source, importReporter);
            List<Token> tokens = lexer.scanTokens();
            if (importReporter.hasErrors()) {
                transferErrors(importReporter);
                return;
            }

            Parser parser = new Parser(tokens, importReporter);
            Program importedProgram = parser.parse();
            if (importReporter.hasErrors()) {
                transferErrors(importReporter);
                return;
            }

            // Recursively resolve imports in the imported file
            ImportResolver childResolver = new ImportResolver(resolved.getParent(), importReporter);
            childResolver.resolvedPaths.addAll(this.resolvedPaths);
            importedProgram = childResolver.resolveImports(importedProgram);
            this.resolvedPaths.addAll(childResolver.resolvedPaths);

            // Merge — selective or all
            if (imp.isSelectiveImport()) {
                Set<String> wanted = new HashSet<>(imp.getNames());
                for (ClassDecl c : importedProgram.getClasses()) {
                    if (wanted.contains(c.getName())) classes.add(c);
                }
                for (InterfaceDecl i : importedProgram.getInterfaces()) {
                    if (wanted.contains(i.getName())) interfaces.add(i);
                }
                for (EnumDecl e : importedProgram.getEnums()) {
                    if (wanted.contains(e.getNameStr())) enums.add(e);
                }
            } else {
                classes.addAll(importedProgram.getClasses());
                interfaces.addAll(importedProgram.getInterfaces());
                enums.addAll(importedProgram.getEnums());
            }

        } catch (IOException e) {
            if (errorReporter != null) {
                errorReporter.error(imp.getSourceLocation(),
                        "Failed to read imported file: " + e.getMessage());
            }
        }
    }

    private void transferErrors(ErrorReporter from) {
        // Transfer errors from imported file reporter to main reporter
        for (var err : from.getErrors()) {
            if (errorReporter != null) {
                errorReporter.error(err.getLocation(), "[import] " + err.getMessage());
            }
        }
    }
}
