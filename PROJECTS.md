# Ordered local projects (experimental)

DhrLang projects use a manifest to load multiple existing `.dhr` files into the
same program. This adds no `import` keyword, package namespace, remote dependency
resolver or numeric-semantic change.

**Availability:** These commands are **unreleased**, available only in source builds
from current `main`. They are not included in the already-published v4.0.2 binaries,
even though current source builds and the example below still use version `4.0.2`.

## Manifest and commands

`dhrlang.json` follows [project.schema.json](project.schema.json):

```json
{
  "schemaVersion": 1,
  "compilerVersion": "4.0.2",
  "profile": "jvm-bytecode-v1",
  "sources": ["Budget.dhr", "Main.dhr"],
  "input": "12500\n50000\n"
}
```

From the repository root after building `stageCompiler`:

```powershell
java -jar build\compiler\DhrLang.jar project check input\projects\budget\dhrlang.json
java -jar build\compiler\DhrLang.jar project run input\projects\budget\dhrlang.json
```

`check` lexes, parses, type-checks and lowers all files into bytecode but does not
execute static initializers or the entry point. `run` executes the combined
program in a fresh, bounded JVM. Both commands use the host profile and limits
documented in [HOST_EXECUTION.md](HOST_EXECUTION.md).

One JSON object is written to stdout:

```json
{
  "schemaVersion": 1,
  "operation": "run",
  "sources": ["Budget.dhr", "Main.dhr"],
  "execution": {
    "schemaVersion": 1,
    "profile": "jvm-bytecode-v1",
    "status": "SUCCESS",
    "stdout": "WITHIN_BUDGET\n"
  }
}
```

The `execution` object above is abbreviated; the real object contains every
field in `host-response.schema.json`, including diagnostics, compiler version,
source identity and timing. Project input is data on stdin, not generated source.
Exit code 0 means success, 64 means an invalid manifest/command, and 2 means a
compilation, execution or worker failure. A successful `check` does not imply
that runtime inputs, native calls or resource budgets will succeed.

## Resolution and compatibility

- Paths are relative to the **resolved manifest directory**, never the caller's
  current directory. Slash and backslash separators are accepted in manifests.
- Sources are read exactly once into a bounded snapshot. There are no globs,
  automatic directory scans, generated file names or implicit dependencies.
- Absolute paths, drive-qualified paths, parent-directory segments, duplicates,
  non-`.dhr` files and symlinks outside the project root are rejected. Only regular
  UTF-8 files are accepted. This is path validation, not protection against a
  malicious actor concurrently changing the filesystem.
- There are at most 32 source files, with at most 131072 source characters in
  total. The manifest/worker JSON limit is 512 KiB. `input` and `limits` behave
  exactly as in the host request; source paths and other fields are strict.
- `compilerVersion` pins an exact version and is checked against the actual
  packaged compiler's manifest before launching a worker. A version is not a
  cryptographic artifact identity; also retain release hashes for reproducibility.
- Files are independently lexed and parsed. An unterminated comment or string in
  one file cannot hide declarations in another.
- Classes and interfaces share the existing global namespace. A class in one
  file can refer to a class in another regardless of file order. Duplicate names,
  missing references and multiple static `main` methods are compile errors.
- Static initializers retain existing declaration order, with file order taken
  from `sources`. No module dependency graph or initialization-cycle semantics
  are invented. Avoid cross-file initialization side effects; references to values
  not yet initialized retain the current compiler/runtime behavior.
- The entry point remains one `static kaam main()`. There is no configurable
  host method call, alternate grammar or EVM execution through this command.

## Diagnostics and source identity

Project diagnostics include `[relative/file.dhr]` in their messages and preserve
the file-local line/column. Synthetic parser tokens retain their original file.
Diagnostic deduplication includes the filename, and a warning suppression in one
source file cannot suppress the same line in another.

`execution.sourceSha256` is a source-bundle identity for project commands. It
hashes the `dhrlang-source-bundle-v1` prefix followed by each UTF-8 path and source,
each prefixed by a four-byte big-endian byte length. Paths use `/` in that identity
on all platforms. Changing a path, source content or source order changes it.
It does not hash stdin or compiler bytes; retain those separately for replay.

The existing single-file `host` request keeps its original JSON shape and raw
source-text SHA-256 behavior.

## What this does not promise

This is an explicit source bundle, **not** a language module/package system.
It does not introduce scoped exports, package installation, semantic-version
resolution, new integer rules or a 5.0 release. The JVM host remains experimental,
not an OS sandbox. See [the compatibility decision](design/compatibility-profiles.md)
for the boundary between this implementation and future breaking proposals.
