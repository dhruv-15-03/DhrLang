# DhrLang Security Rules

The DhrLang compiler ships a built-in smart-contract security auditor. Run it with:

```bash
java -jar DhrLang.jar --audit              path/to/Contract.dhr   # human-readable report
java -jar DhrLang.jar --audit --json       path/to/Contract.dhr   # machine-readable JSON
java -jar DhrLang.jar --audit --sarif --output=build/sarif Contract.dhr   # SARIF 2.1.0
```

`--audit` is an **analysis mode**: it always exits `0` and reports problems as
*findings* rather than failing the build, so it runs even on contracts that do
not yet type-check. In CI, [`contract-audit.yml`](.github/workflows/contract-audit.yml)
emits one SARIF file per contract and uploads them to the repository's
**Security → Code scanning** tab (category `dhrlang-security-audit`).

## How findings map to SARIF

| Audit severity        | SARIF `level` | Security tab |
| --------------------- | ------------- | ------------ |
| `CRITICAL`, `HIGH`    | `error`       | Error        |
| `MEDIUM`              | `warning`     | Warning      |
| `LOW`, `INFORMATIONAL`| `note`        | Note         |

Each result also carries:

- **`region.startLine`** — the source line of the offending function, when the
  analyzer knows it (omitted otherwise, so the alert still lands at the file).
- **`partialFingerprints["dhrlangAuditFingerprint/v1"]`** — a stable SHA-256 of
  the rule id + logical location + title. GitHub uses it to track an alert
  across runs and dedupe identical findings even as line numbers shift.

Rule ids are stable and grouped into families. Anchors below match the `helpUri`
emitted in the SARIF, so "Open documentation" from the Security tab lands here.

---

## ARITH — arithmetic safety

Detected by `ArithmeticOverflowDetector` on `num` (uint256) arithmetic that
writes to storage. Maps to **SWC-101 (Integer Overflow and Underflow)**.
Severity is `HIGH` when unguarded, `LOW` when a guarding `require`/check is found.

### ARITH-ADDITION_OVERFLOW
An addition that writes a storage field may overflow uint256.
*Fix:* guard with `require(result >= a, "overflow")` or compile the function
`@checked` so the EVM backend reverts on overflow.

### ARITH-SUBTRACTION_UNDERFLOW
A subtraction that writes a storage field may underflow below zero.
*Fix:* `require(a >= b, "underflow")` before subtracting, or use `@checked`.

### ARITH-MULTIPLICATION_OVERFLOW
A multiplication that writes a storage field may overflow uint256.
*Fix:* guard the result, or use `@checked` (which lowers to a SafeMath-style
identity check).

### ARITH-DIVISION_BY_ZERO
A division whose divisor is not provably non-zero.
*Fix:* `require(divisor != 0, "div by zero")`.

---

## SEC — semantic security analysis

Detected by `SecurityAnalyzer` (privilege, taint, loop-bound, reentrancy, and
tx.origin passes).

### SEC-PRIVILEGE
`CRITICAL` — a function modifies a privileged storage field (e.g. an owner /
admin field) without a `msg.sender` access-control guard. Maps to
**SWC-105 (Unprotected access)** / **SWC-100 (Default visibility)**.
*Fix:* `if (msg.sender != owner) { throw "Not authorized"; }` before the write.

### SEC-TAINT
`HIGH` — a function parameter flows directly into a storage write with no
validation. Related to **SWC-123 (Requirement violation)**.
*Fix:* validate the input (range / non-zero / non-null-address) before storing.

### SEC-LOOP_BOUND
`CRITICAL` for `while (true)` infinite loops, `MEDIUM` for nested loops. Maps to
**SWC-128 (DoS with block gas limit)**.
*Fix:* add a bounded counter / break condition, or move iteration off-chain.

### SEC-REENTRANCY
`HIGH` — a storage field is written **after** an external call in the same
function (a checks-effects-interactions violation), so a reentrant call can
observe stale state and drain funds. External calls are value transfers
(`this.transfer(to, amount)`) and method calls on an `Address`-typed storage
field or parameter (`token.transfer(...)`). Maps to **SWC-107 (Reentrancy)**.
*Fix:* update all storage **before** the external call, or annotate the function
`@nonreentrant` (which the analyzer treats as an explicit guard and trusts).

### SEC-TX_ORIGIN
`HIGH` — `tx.origin` is used in an equality check for authorization. `tx.origin`
is the original externally-owned account and can be spoofed when the user is
phished into calling a malicious intermediary contract. Maps to
**SWC-115 (Authorization through tx.origin)**.
*Fix:* authorize with `msg.sender` instead of `tx.origin`.

---

## INV — invariant checks

Detected by `InvariantChecker`: a state-modifying function can break an
invariant inferred from a field's declaration or guards. All `HIGH`.

### INV-NON_NEGATIVE
A field expected to stay `>= 0` can be driven negative.

### INV-NON_ZERO
A field expected to stay `!= 0` can be zeroed.

### INV-UPPER_BOUND
A field can exceed its declared/maximum upper bound.

### INV-LOWER_BOUND
A field can drop below its declared lower bound.

### INV-NOT_NULL_ADDRESS
An `Address` field expected to be non-zero can be set to `address(0)`.

### INV-MONOTONIC_INC
A field expected to only increase can be decreased.

### INV-MONOTONIC_DEC
A field expected to only decrease can be increased.

*Fix (all):* add a `require`/guard before the state change so the invariant
provably holds.

---

## AUD — contract hygiene heuristics

Higher-level structural checks produced directly by the auditor.

### AUD-001
`HIGH` — a state-changing `@payable` function has no `@nonreentrant` guard.
Maps to **SWC-107 (Reentrancy)**. *Fix:* add `@nonreentrant`.

### AUD-002
`MEDIUM` — `@payable` functions exist without reentrancy protection.
*Fix:* add `@nonreentrant` to payable functions.

### AUD-003
`LOW` — a read-only-looking function is not marked `@view`.
*Fix:* mark it `@view` to save callers gas.

### AUD-004
`MEDIUM` — the contract uses more storage slots than the recommended limit.
*Fix:* pack or refactor storage.

### AUD-005
`INFORMATIONAL` — a `@contract` has no `@constructor`.
*Fix:* add a `@constructor` to initialize state.

### AUD-010
`HIGH` — two contracts share the same name. *Fix:* rename one.

### AUD-011
`INFORMATIONAL` — a contract declares no functions. *Fix:* add functions or
remove the contract.

---

## DHR-E5xx — contract validation

`ContractValidator` errors (e.g. `DHR-E550` "@payable function does not validate
`msg.value`") are also surfaced as findings, keyed by their `DHR-Exxx` code.
These are documented in [`ERROR_CODES.md`](ERROR_CODES.md).
