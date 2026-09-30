# Experimental bounded JVM host interface

`java -jar DhrLang.jar host request.json` runs one source program in a fresh JVM
and emits exactly one versioned JSON response. This is an additive tool boundary
for local teaching tools and future adapters, not a new language grammar or a
production multi-tenant sandbox.

## Request

The contract is [host-request.schema.json](host-request.schema.json). Requests
are limited to 512 KiB encoded JSON, 128 Ki characters of source, and 64 Ki
characters of explicit standard input. Unknown fields, duplicate keys, coerced
versions/limit values and trailing JSON are rejected.

```json
{
  "schemaVersion": 1,
  "profile": "jvm-bytecode-v1",
  "source": "class Main { static kaam main() { printLine(toNum(readLine()) + 2); } }",
  "input": "5\n",
  "limits": {
    "timeoutMs": 5000,
    "maxSteps": 1000000,
    "heapMb": 128,
    "maxOutputBytes": 65536
  }
}
```

Omitting `input` means an empty, closed input stream. Omitting `limits` selects
the values above. If supplied, all four limit fields are required:

| Limit | Range | Enforcement |
|---|---|---|
| `timeoutMs` | 1-30000 | Parent kills the worker after the budget, including JVM startup/compilation |
| `maxSteps` | 100-5000000 | Bytecode instruction counter; exceeding it is a runtime failure |
| `heapMb` | 32-256 | Child JVM maximum Java heap, not a total process/RSS limit |
| `maxOutputBytes` | 256-1048576 | Combined stdout/stderr bytes; exceeding the budget kills the worker |

The entry point remains `static kaam main()`. Input is data on stdin, never
interpolated into the source. The first protocol version accepts one source
unit, not a module graph, an arbitrary host method, uploaded bytecode or a shell
command. Core JVM numeric semantics remain unchanged.

## Response

The contract is [host-response.schema.json](host-response.schema.json).
The response identifies the compiler version, profile and SHA-256 of the source,
and separates program stdout/stderr from structured diagnostics.

| Status | Meaning |
|---|---|
| `SUCCESS` | The worker completed normally; program output is in `stdout` |
| `COMPILE_ERROR` | Lexing, parsing, typing, lowering or profile selection rejected the source |
| `RUNTIME_ERROR` | Uncaught program error or VM instruction-budget exhaustion |
| `INVALID_REQUEST` | Missing/unsupported fields, invalid limits, malformed/oversized JSON |
| `TIME_LIMIT` | Parent terminated a worker that exceeded its wall-clock budget |
| `OUTPUT_LIMIT` | Parent terminated output flooding; only the bounded prefix is returned |
| `WORKER_ERROR` | Launch, serialization, bytecode validation or unexpected child-process failure |

The CLI exits 0 only for `SUCCESS`, 64 for `INVALID_REQUEST`, and 2 for other
failures. `workerExitCode` is the subprocess code, not program success: a worker
can successfully serialize a compilation/runtime failure and exit 0.
It is -1 when no worker result was available.

At most 50 compiler diagnostics are returned; individual diagnostic messages
are capped at 2048 characters. Source line/column 0 means unknown, never a
guessed location. Timing is observational and is not deterministic.

## Isolation and capability boundary

Every request gets a separate process and temporary working directory. The
worker does not inherit tokens, passwords, `CLASSPATH`, JVM injection options
or the caller's home directory. Only Windows `SystemRoot`, when needed to launch
Java, is copied. Temporary request/response files are removed after the run.

The bytecode profile uses strict validation and a 256-frame call-depth cap.
Native dispatch is explicitly allowlisted: console output, supplied input,
conversion, deterministic math/string/array functions and type queries.
`clock`, random functions and `sleep` are denied. New native functions are not
automatically available to this profile. EVM contracts, deployment, wallet tools,
network/database APIs and process launch are not exposed.

**This is not an OS sandbox.** Java heap size does not bound all native memory,
and a VM/compiler/JVM defect may defeat language-level assumptions. Do not
serve hostile users or expose customer credentials in the same trust boundary
without OS/container isolation, total CPU/memory/process quotas, filesystem and
network policy, and an independent review.

The host envelope does not make a business rule correct. For an enterprise
adapter, validate the input and interpret output with a separately versioned
domain schema. Keep SAP authentication, authorization, transactions, write
approval and idempotency outside the learner program.

## Stage 3 compatibility decisions

This increment implements the execution boundary only:

- No arithmetic-default change: JVM signed 64-bit behavior is preserved.
- No `import` or package syntax is introduced. Module resolution, dependency
  pinning and initialization rules need an explicit conformance/migration design.
- No general in-process embedding guarantee. The existing native runtime bridge
  is isolated by one worker process per request, not declared thread-safe.
- The host profile is experimental and bytecode-only. An accepted request can
  still return a compile or runtime failure for an unsupported construct.
- Stable 5.0 promotion, real learner cohorts and live SAP integration remain
  separate gates.

Tests run through the actual packaged JAR and cover successful input/output,
invalid requests, schema conformance, compilation/runtime failures, output/time/
step limits, denied nondeterministic calls and concurrent request isolation.
