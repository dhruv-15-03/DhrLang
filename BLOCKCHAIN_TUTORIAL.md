# DhrLang Blockchain Tutorial: Write Ã¢â€ â€™ Compile Ã¢â€ â€™ Deploy Ã¢â€ â€™ Verify

> **End-to-end guide** for building, deploying, and verifying smart contracts using DhrLang.

---

## Prerequisites

- Java 17+ installed
- DhrLang 4.0.2 JAR (`DhrLang-4.0.2.jar`)
- (Optional) Foundry toolkit for local testing: https://book.getfoundry.sh/

```bash
java -jar DhrLang-4.0.2.jar --version
# Ã¢â€ â€™ DhrLang version 4.0.2
```

---

## Step 1: Write Your Contract

Create a file `MyToken.dhr`:

```dhrlang
@contract
class MyToken {
    @storage sab name;
    @storage sab symbol;
    @storage num totalSupply;
    @storage num maxSupply;
    @storage Address owner;

    @constructor
    kaam init(sab _name, sab _symbol, num _initialSupply, num _maxSupply) {
        if (_maxSupply <= 0) {
            throw "Max supply must be positive";
        }
        if (_initialSupply > _maxSupply) {
            throw "Initial supply exceeds max supply";
        }
        name = _name;
        symbol = _symbol;
        owner = msg.sender;
        maxSupply = _maxSupply;
        totalSupply = _initialSupply;
    }

    @view
    kaam getName() {
        return name;
    }

    @view
    kaam getTotalSupply() {
        return totalSupply;
    }

    kaam mint(Address to, num amount) {
        if (msg.sender != owner) {
            throw "Only owner can mint";
        }
        if (amount <= 0) {
            throw "Amount must be positive";
        }
        // Bound `amount` first so the subtraction below cannot underflow, then
        // compare the accumulator directly. Writing this as
        // `totalSupply + amount > maxSupply` would perform the very addition
        // the guard exists to prevent.
        if (amount > maxSupply) {
            throw "Amount exceeds max supply";
        }
        if (totalSupply > maxSupply - amount) {
            throw "Mint would exceed max supply";
        }
        totalSupply = totalSupply + amount;
    }

    @nonreentrant
    kaam transfer(Address to, num amount) {
        require(amount > 0, InvalidAmount(amount));
        totalSupply = totalSupply;
    }

    @event
    kaam Transfer(indexed Address from, indexed Address to, num amount) {}

    @error
    kaam InvalidAmount(num amount) {}
}
```

Key annotations:
- `@contract` â€” marks the class as a smart contract
- `@storage` â€” fields persisted on-chain (EVM storage slots)
- `@constructor` â€” runs once at deployment
- `@view` â€” read-only (no gas for external calls)
- `@nonreentrant` â€” compiler enforces reentrancy protection
- `@event` â€” emits EVM log events; mark params `indexed` to make them filterable topics
- `@error` â€” declares a gas-efficient custom error; raise it with `revert(ErrName(args))`
  or `require(cond, ErrName(args))`
- `@checked` / `@unchecked` â€” select overflow behaviour for `+`, `-`, `*` on `num` in a method.
  `@checked` reverts on overflow/underflow; `@unchecked` wraps modulo 2Â²âµâ¶. **As of v4.0.0
  arithmetic is checked by default** (Solidity 0.8+ model); add `@unchecked` to opt back into
  wrapping where it is intentional.
- `@requires(expr)` / `@ensures(expr)` â€” design-by-contract pre/postconditions on a method.
  `@requires` is checked at entry (reverts `precondition failed`); `@ensures` is checked at
  every return (reverts `postcondition failed`) and may reference `result`, the return value.
- `@invariant(expr)` â€” a contract-level invariant (declared next to `@contract`), re-checked
  after every state-mutating method and reverting `invariant violated`.

### Transaction context globals

Inside a contract you can read the transaction and block context directly:

| Expression | Type | EVM | Meaning |
|------------|------|-----|---------|
| `msg.sender` | `Address` | `CALLER` | Account that called this contract |
| `msg.value` | `uint256` | `CALLVALUE` | Wei sent with the call |
| `msg.data.length` | `uint256` | `CALLDATASIZE` | Size of the calldata, in bytes |
| `msg.sig` | `uint256` | `calldataload(0) >> 224` | 4-byte function selector |
| `block.timestamp` / `block.number` | `uint256` | `TIMESTAMP` / `NUMBER` | Current block |
| `tx.origin` | `Address` | `ORIGIN` | Original external sender (avoid for auth) |

`msg.data` itself is not a value (DhrLang has no dynamic `bytes`): use `msg.data.length`
for the calldata size and `msg.sig` for the selector. Reading any other `msg.data.<x>`,
or using bare `msg.data`, is a type error. These are transaction-context reads, so a
`@view` method may use them but a `@pure` method may not.

### Arithmetic safety example

As of **v4.0.0**, `num` arithmetic (`+`, `-`, `*`) **reverts on overflow/underflow by
default** â€” you no longer need `@checked` for safe math. Use `@unchecked` to opt back
into wrapping where it is intentional:

```dhrlang
@contract
class Vault {
    @storage num balance;

    // Checked by default in v4.0.0: reverts with "arithmetic overflow" /
    // "arithmetic underflow". (@checked is now redundant but still allowed.)
    kaam deposit(num amount) {
        balance = balance + amount;
    }

    // Opt out for hot paths where wrapping modulo 2Â²âµâ¶ is intentional.
    @unchecked
    kaam wrappingAdd(num a, num b) {
        balance = a + b;
    }
}
```

### Design-by-contract example

```dhrlang
// Invariant re-checked after every state change: the bank can never go negative.
@invariant(balance >= 0)
@contract
class Bank {
    @storage num balance;

    // Precondition: callers may only deposit a positive amount.
    @requires(amount > 0)
    kaam deposit(num amount) {
        balance = balance + amount;
    }

    // Precondition + postcondition working together.
    @requires(amount <= balance)
    @ensures(balance >= 0)
    kaam withdraw(num amount) {
        balance = balance - amount;
    }

    // `result` binds to the return value inside @ensures.
    @ensures(result >= 0)
    @view
    num currentBalance() {
        return balance;
    }
}
```

> A typo'd name inside a spec (e.g. `@requires(amunt > 0)`) is rejected at compile time
> with **DHR-E516** â€” on the EVM an unresolved identifier would silently compile to `0`.

### Fuzz your specs (`contract fuzz`)

Specs are only useful if they actually hold. The fuzzer searches for an input that
*falsifies* an `@ensures` or `@invariant`, so you can catch a logic bug before you deploy:

```bash
java -jar DhrLang-4.0.2.jar contract fuzz MyToken.dhr
# reproducible run, more iterations:
java -jar DhrLang-4.0.2.jar contract fuzz --runs=512 --seed=42 MyToken.dhr
```

It runs each function over a simulated EVM state (uint256 wrapping arithmetic, `@checked`
overflow reverts, storage and mappings default to `0`) and prints a per-function tally.
If a spec is violated it reports a **minimized counterexample**:

```
  Buggy::set â€” 256 runs: 0 ok, 256 violations, 0 reverts, 0 skipped, 0 errors

Failing inputs:
  âœ— Buggy::set(0, 0) â†’ invariant violated: @invariant(total == a + b)

Result: ISSUES FOUND âœ—
```

The fuzzer is **sound, not complete**: it only flags a violation on a faithful execution.
Inputs that fail a `@requires` precondition are reported as *skipped* (out of scope), and
anything it cannot model faithfully (e.g. a call into another function) is skipped rather
than reported as a false positive. The command exits non-zero when a counterexample is
found, so `contract fuzz` works as a CI gate.

### Prove your specs (`contract prove`, experimental)

Fuzzing *samples* inputs; **proving** tries to settle a spec for **every** input. The
`contract prove` command (provable-safety level **L2b**, experimental) symbolically
executes each function - forking on `if`/`@requires` and normalizing integer expressions
into a linear-arithmetic form - then runs a **Fourier-Motzkin** decision procedure to
discharge each `@ensures`/`@invariant` as `PROVED`, `REFUTED`, or `UNKNOWN`:

```bash
java -jar DhrLang-4.0.2.jar contract prove MyToken.dhr
# widen the counterexample search radius (default 8); JSON for CI:
java -jar DhrLang-4.0.2.jar contract prove --bound=12 --json MyToken.dhr
```

```
Contract Math
  add(num a, num b)
    @ensures((result >= a)) ............................ PROVED
  addBug(num a, num b)
    @ensures((result > a)) ............................. REFUTED  a=0, b=0 -> postcondition violated: @ensures((result > a))
  sub(num a, num b)
    @ensures((result == (a - b))) ...................... PROVED

Summary: 2 proved, 1 refuted, 0 unknown across 3 function(s).
```

Proving is **sound only under checked arithmetic** (overflow reverts), so it is enabled
for `@checked` functions; an unchecked function's obligations are reported `UNKNOWN`
(refutation still runs). A `REFUTED` result always carries a **concrete counterexample**,
cross-checked by the same engine that backs `contract fuzz`, so a refutation is never a
false alarm. Anything the linear model can't capture - loops, mapping aliasing, external
calls - is `UNKNOWN` rather than a guess. Like `fuzz`, it exits non-zero on any refutation,
so it slots into CI next to `fuzz`/`safety`.

---

## Start From a Standard-Library Template

Don't write boilerplate from scratch. DhrLang ships eight audited-pattern base contracts
you can browse and scaffold straight from the CLI:

```bash
# Browse the catalog:
java -jar DhrLang-4.0.2.jar contract stdlib list

# Read a template's source:
java -jar DhrLang-4.0.2.jar contract stdlib show ERC20

# Scaffold a ready-to-edit file (optionally renaming the contract):
java -jar DhrLang-4.0.2.jar contract stdlib new Ownable --name=MyToken --output=input/contracts
```

| Template | What you get |
|----------|--------------|
| `Ownable` | Single-owner access control (`transferOwnership`, `renounceOwnership`) |
| `ReentrancyGuard` | Runtime reentrancy mutex |
| `Pausable` | Emergency pause / unpause switch |
| `SafeMath` | Checked `add`/`sub`/`mul`/`div`/`mod` |
| `ERC20` | Fungible-token **starter scaffold** (EIP-20 surface; transfer logic stubbed) |
| `ERC721` | NFT **starter scaffold** (EIP-721 surface; transfer logic stubbed) |
| `AccessControl` | Role-based access control (grant / revoke / check) |
| `TimelockController` | Governance timelock (schedule / execute / cancel) |

`Ownable`, `Pausable`, `ReentrancyGuard`, `SafeMath`, `AccessControl` and
`TimelockController` are complete patterns; the `ERC20`/`ERC721` token templates are
honest starting points whose per-account bookkeeping you fill in. The owner-guard in
`Ownable` uses the `address(0)` builtin (new in v3.8.0) to reject the zero address.

---

## Step 2: Compile to EVM Bytecode

```bash
java -jar DhrLang-4.0.2.jar contract compile MyToken.dhr
```

Output:
```
1 contract(s) compiled Ã¢â€ â€™ /path/to/build/evm
```

Generated artifacts in `build/evm/`:
- `MyToken.bin` Ã¢â‚¬â€ creation bytecode (deployed to chain)
- `MyToken.runtime.bin` Ã¢â‚¬â€ runtime bytecode (stored on-chain)
- `MyToken.abi.json` Ã¢â‚¬â€ ABI for tools (ethers.js, Foundry, etc.)

---

## Step 3: Estimate Gas Costs

```bash
java -jar DhrLang-4.0.2.jar contract gas MyToken.dhr
```

Output:
```
Ã¢â€¢â€Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢â€”
Ã¢â€¢â€˜                    GAS ESTIMATION REPORT                    Ã¢â€¢â€˜
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
Ã¢â€¢â€˜  Bytecode size:              342 bytes                      Ã¢â€¢â€˜
Ã¢â€¢â€˜  Storage slots:                4                            Ã¢â€¢â€˜
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
Ã¢â€¢â€˜  Gas Breakdown:                                             Ã¢â€¢â€˜
Ã¢â€¢â€˜    Intrinsic (tx base):        21,000 gas                   Ã¢â€¢â€˜
Ã¢â€¢â€˜    Calldata:                    4,872 gas                   Ã¢â€¢â€˜
Ã¢â€¢â€˜    Contract creation:          32,000 gas                   Ã¢â€¢â€˜
Ã¢â€¢â€˜    Code deposit:               68,400 gas                   Ã¢â€¢â€˜
Ã¢â€¢â€˜    Constructor execution:      90,000 gas                   Ã¢â€¢â€˜
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
Ã¢â€¢â€˜  TOTAL ESTIMATED GAS:         216,272                       Ã¢â€¢â€˜
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
Ã¢â€¢â€˜  Cost at 30 gwei:            0.006488 ETH (~$16.22 @ $2500) Ã¢â€¢â€˜
Ã¢â€¢Å¡Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
```

For JSON output (CI/tools):
```bash
java -jar DhrLang-4.0.2.jar contract gas --json MyToken.dhr
```

---

## Step 4: Test Locally (Anvil/Hardhat)

Start a local Ethereum node:
```bash
# Using Foundry's Anvil
anvil
# Ã¢â€ â€™ Listening on 127.0.0.1:8545
# Ã¢â€ â€™ Private Key: 0xac0974bec...
```

Deploy locally:
```bash
java -jar DhrLang-4.0.2.jar contract deploy --network=local MyToken.dhr
```

Or generate a deploy script:
```bash
java -jar DhrLang-4.0.2.jar contract deploy --network=local --dry-run --deploy-format=ethers MyToken.dhr
```

This generates `build/evm/Deploy.deploy.js` Ã¢â‚¬â€ run it with:
```bash
RPC_URL=http://127.0.0.1:8545 \
PRIVATE_KEY=0xac0974bec... \
node build/evm/Deploy.deploy.js
```

---

## Step 5: Setup Your Wallet

**Option A: Environment variable** (simplest for development)
```bash
export DHRLANG_PRIVATE_KEY=0xYourPrivateKeyHere
```

**Option B: Encrypted keystore** (recommended for production)
```bash
java -jar DhrLang-4.0.2.jar contract wallet create
# Enter private key (hex): ****
# Enter keystore password: ****
# Confirm password: ****
# Ã¢â€ â€™ Keystore created at ~/.dhrlang/keystore.enc
```

Show your wallet address:
```bash
java -jar DhrLang-4.0.2.jar contract wallet show
# Ã¢â€ â€™ Address: 0x1234...abcd
# Ã¢â€ â€™ Key source: KEYSTORE_FILE
```

---

## Step 6: Deploy to Testnet (Sepolia)

```bash
# Get Sepolia ETH from a faucet: https://sepoliafaucet.com

java -jar DhrLang-4.0.2.jar contract deploy --network=sepolia MyToken.dhr
```

Output:
```
Ã¢â€¢â€Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢â€”
Ã¢â€¢â€˜              DhrLang Contract Deployment                     Ã¢â€¢â€˜
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
  Network:    Sepolia Testnet (chainId: 11155111)
  Contracts:  1
  MyToken: ~216,272 gas
  Deployer:   0x1234...abcd
  Key source: ENVIRONMENT_VARIABLE
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
  Building tx for MyToken...
  Signed: 0x02f9...
  Raw signed tx written to build/evm/MyToken.signed.tx
Ã¢â€¢Â Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â£
  Next steps:
    1. Broadcast: cast send --raw <signed.tx> --rpc-url https://sepolia.infura.io/v3/{API_KEY}
    2. Verify:    dhrlang contract verify --address=<deployed> --network=sepolia MyToken.dhr
Ã¢â€¢Å¡Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
```

Broadcast using Foundry:
```bash
cast publish --rpc-url https://sepolia.infura.io/v3/YOUR_KEY \
  "$(cat build/evm/MyToken.signed.tx)"
```

### Broadcast artifacts + one-command verify

Every `contract deploy` (live, offline, or `--dry-run`) writes a
**Foundry-compatible broadcast artifact**:

```
build/evm/broadcast/Deploy.s.sol/<chainId>/run-latest.json        # live
build/evm/broadcast/Deploy.s.sol/<chainId>/dry-run/run-latest.json # --dry-run
```

It records each CREATE transaction, the deployed (or, for dry runs, the
**predicted**) contract address, and a populated `receipts[]` for live deploys -
the same schema `forge script --broadcast` emits, so DhrLang deployments drop
straight into Foundry-shaped tooling, indexers, and CI.

Dry runs predict each address deterministically from `sender` + `nonce`
(`keccak256(rlp([sender, nonce]))[12:]`). Use `--from=<0x..>` to choose the
deployer; local deploys default to Anvil account #0, so a fresh local deploy is
predicted at the canonical `0x5fbd...0aa3`:

```bash
java -jar DhrLang-4.0.2.jar contract deploy --network=local --dry-run MyToken.dhr
#   MyToken -> 0x5fbdb2315678afecb367f032d93f642f64180aa3 (predicted, nonce 0)
```

Pass `--verify` to **deploy and verify in one command** - source verification runs
automatically after a successful live deploy (and degrades gracefully, printing the
manual follow-up, when no explorer API key is set):

```bash
export DHRLANG_ETHERSCAN_API_KEY=your_api_key
java -jar DhrLang-4.0.2.jar contract deploy --network=sepolia --verify MyToken.dhr
```

---

## Step 7: Verify on Etherscan

```bash
export DHRLANG_ETHERSCAN_API_KEY=your_api_key

java -jar DhrLang-4.0.2.jar contract verify \
    --address=0xDeployedContractAddress \
    --network=sepolia \
    MyToken.dhr
```

Output:
```
Verifying MyToken at 0xDeployed... on Sepolia Testnet...
  Ã¢Å“â€œ Contract verified successfully!
  View: https://sepolia.etherscan.io/address/0xDeployed#code
```

---

## Step 8: Security Audit

Run the built-in security auditor before deploying to mainnet:

```bash
java -jar DhrLang-4.0.2.jar --audit MyToken.dhr
```

This checks for:
- Reentrancy vulnerabilities
- Checks-effects-interactions pattern violations
- View/pure function state access violations
- Storage layout issues
- Access control gaps

### Safety report + CI gate (`contract safety`)

For a single, gradeable verdict that also gates CI, use `contract safety`. It runs the
audit **and** the L3 spec fuzzer, folds any invariant/postcondition counterexample in as a
`FUZZ-INVARIANT` finding, and prints a Markdown report led by a **safety score**
(`100 - risk`) and an **A-F grade**:

```bash
java -jar DhrLang-4.0.2.jar contract safety MyToken.dhr
# report-only (don't fail the build):
java -jar DhrLang-4.0.2.jar contract safety --fail-on=none MyToken.dhr
```

It also writes `safety.sarif` (ingestible by GitHub Code Scanning) and `safety-report.md`
to the output directory. The command exits non-zero when any finding meets the `--fail-on`
threshold (`critical|high|medium|low|none`, default `high`), so it drops straight into a
pipeline.

---

## Step 9: Deploy to Mainnet

When ready for production:

```bash
# Dry run first!
java -jar DhrLang-4.0.2.jar contract deploy --network=mainnet --dry-run MyToken.dhr

# Real deployment
java -jar DhrLang-4.0.2.jar contract deploy --network=mainnet MyToken.dhr
```

---

## Step 10: Export for Hardhat / Foundry / viem

Already have a JavaScript/TypeScript or Solidity-tooling project? `contract export`
projects every `@contract` into the artifact shapes those toolchains already consume,
so a DhrLang contract drops in without hand-copying ABIs or bytecode:

```bash
# Emit all three targets into build/contracts/ (default):
java -jar DhrLang-4.0.2.jar contract export MyToken.dhr

# Pick a single target and destination:
java -jar DhrLang-4.0.2.jar contract export --format=ts --output=app/src/contracts MyToken.dhr
```

`--format=all` (default) writes, per contract:

| Path | Consumed by |
|------|-------------|
| `hardhat/<Name>.json` | Hardhat, ethers, viem, wagmi (`hh-sol-artifact-1`) |
| `foundry/<Name>.json` | Foundry / `forge` (`out/`-shaped artifact) |
| `ts/<Name>.ts` | viem / wagmi - ABI exported `as const` + `0x` bytecode consts |
| `ts/index.ts` | Barrel re-exporting every generated module |

Narrow the output with `--format=hardhat|foundry|ts`. The ABI `as const` assertion in the
TypeScript module is what lets viem and wagmi infer fully-typed `read`/`write` calls. Export
is a read-only projection of the compiled artifacts, so it never changes codegen or audit
results, and the generated files are deterministic ASCII.

---

## Step 11: Account Abstraction (ERC-4337)

Build and hash [ERC-4337](https://eips.ethereum.org/EIPS/eip-4337) UserOperations
**entirely offline** - no bundler or RPC node required. This is everything the
smart-account owner needs to produce the value they sign; only the final
`eth_sendUserOperation` submission needs a live bundler.

Look up the canonical EntryPoint (identical on every chain via CREATE2):

```bash
java -jar DhrLang.jar contract account entrypoint            # v0.6 (default)
java -jar DhrLang.jar contract account entrypoint --version=0.7
```

Build a v0.6 UserOperation and compute its deterministic `userOpHash`:

```bash
java -jar DhrLang.jar contract account userop \
    --sender=0xYourSmartAccount \
    --nonce=1 \
    --call-data=0xb61d27f6... \
    --call-gas=100000 --verification-gas=200000 --pre-verification-gas=21000 \
    --max-fee=1000000000 --max-priority-fee=1000000000 \
    --network=base
```

The command prints the UserOperation in the `eth_sendUserOperation` JSON shape plus:

```
  userOpHash: 0x994c271a... (the value the account owner signs)
```

`userOpHash` is the canonical v0.6
`keccak256(abi.encode(keccak256(pack(op)), entryPoint, chainId))` - validated
byte-for-byte against ethers.js reference vectors. It is **chain-scoped**: the same
UserOperation produces a different hash per network, which is why `--network` matters.
Add `--json` for a machine-readable object. v0.7 `userOpHash` (a different packing) is
intentionally rejected by `userop`; use the default `--version=0.6`.

| Flag | Meaning | Default |
|------|---------|---------|
| `--sender=0x..` | Smart-account address (required) | - |
| `--nonce=N` | UserOperation nonce (decimal or `0x`) | `0` |
| `--call-data=0x..` | Encoded account `execute(...)` calldata | `0x` |
| `--init-code=0x..` | Factory + calldata for first-time deploy | `0x` |
| `--paymaster-data=0x..` | `paymasterAndData` (gas sponsorship) | `0x` |
| `--call-gas`, `--verification-gas`, `--pre-verification-gas` | Gas limits | `0` |
| `--max-fee`, `--max-priority-fee` | EIP-1559 fees (wei) | `0` |
| `--version=0.6\|0.7` | EntryPoint version | `0.6` |
| `--network=<name>` | Target chain (sets `chainId`) | `local` |

---

## Supported Networks

```bash
java -jar DhrLang-4.0.2.jar contract networks
```

| Network | Chain ID | Type | CLI Name |
|---------|----------|------|----------|
| Ethereum Mainnet | 1 | L1 | `mainnet` |
| Sepolia Testnet | 11155111 | Testnet | `sepolia` |
| Arbitrum One | 42161 | L2 | `arbitrum` |
| Arbitrum Sepolia | 421614 | Testnet | `arbitrum-sepolia` |
| Base | 8453 | L2 | `base` |
| Base Sepolia | 84532 | Testnet | `base-sepolia` |
| Optimism | 10 | L2 | `optimism` |
| Optimism Sepolia | 11155420 | Testnet | `op-sepolia` |
| Polygon | 137 | Sidechain | `polygon` |
| Polygon Amoy | 80002 | Testnet | `amoy` |
| zkSync Era | 324 | ZK rollup | `zksync` / `era` |
| zkSync Sepolia | 300 | Testnet | `zksync-sepolia` |
| Polygon zkEVM | 1101 | ZK rollup | `zkevm` |
| Polygon zkEVM Cardona | 2442 | Testnet | `cardona` |
| Scroll | 534352 | ZK rollup | `scroll` |
| Scroll Sepolia | 534351 | Testnet | `scroll-sepolia` |
| Linea | 59144 | ZK rollup | `linea` |
| Linea Sepolia | 59141 | Testnet | `linea-sepolia` |
| Blast | 81457 | L2 | `blast` |
| Blast Sepolia | 168587773 | Testnet | `blast-sepolia` |
| Local (Anvil) | 31337 | Local | `local` |

> You can also pass the raw numeric chain ID to `--network` (e.g. `--network=534352`).

---

## Quick Reference

| Command | Description |
|---------|-------------|
| `contract compile <file>` | Compile to EVM bytecode + ABI |
| `contract stdlib <list\|show\|new> [Name]` | Browse & scaffold standard base contracts |
| `contract gas <file>` | Gas cost estimation report |
| `contract fuzz [--runs=N] [--seed=N] <file>` | Property-fuzz `@ensures`/`@invariant` specs |
| `contract prove [--bound=N] [--json] <file>` | Statically prove `@ensures`/`@invariant` for all inputs (L2b, experimental) |
| `contract safety [--fail-on=<sev>] <file>` | Audit + fuzz -> safety score, grade, SARIF; CI gate |
| `contract export [--format=<fmt>] [--output=<dir>] <file>` | Emit Hardhat / Foundry / viem artifacts |
| `contract account entrypoint [--version=0.6\|0.7]` | Print the canonical ERC-4337 EntryPoint address |
| `contract account userop --sender=0x.. [--network=<net>]` | Build a UserOperation + compute `userOpHash` (offline) |
| `contract deploy --network=<net> <file>` | Build + sign + deploy (writes a Foundry broadcast artifact) |
| `contract deploy --network=<net> --verify <file>` | Deploy **and** verify in one command |
| `contract deploy --network=<net> --dry-run <file>` | Simulate + predict CREATE addresses (no tx sent) |
| `contract verify --address=<addr> <file>` | Verify on block explorer |
| `contract wallet create` | Create encrypted keystore |
| `contract wallet show` | Show wallet address |
| `contract networks` | List supported chains |
| `contract status --address=<addr>` | Check deployment status |
| `--audit <file>` | Security audit report |
| `--docs <file>` | Generate contract docs |
| `--debug-evm <file>` | Interactive EVM debugger |

---

## Sample Contracts

Reference contracts in `input/contracts/`. Every constructor takes an explicit
upper bound for each accumulating field, so the totals cannot wrap:

| File | Description | Bounded by |
|------|-------------|------------|
| `ERC20Token.dhr` | Standard fungible token (ERC-20) | `maxSupply` |
| `ERC721NFT.dhr` | Non-fungible token (ERC-721) | `maxSupply` |
| `MultiSigWallet.dhr` | M-of-N multi-signature wallet | `maxTransactions` |
| `StakingVault.dhr` | Token staking with rewards | `maxTotalStaked`, `maxStakers` |

The guards are written as

```dhrlang
if (totalSupply > maxSupply - amount) { throw "Mint would exceed max supply"; }
```

rather than the more obvious

```dhrlang
if (totalSupply + amount > maxSupply) { ... }   // wrong
```

because the second form performs the very addition it is meant to protect, and
on a 256-bit target it overflows before the comparison can reject it. Where the
subtraction could itself underflow, the operand is bounded first.

These contracts are audited on every push by
[`contract-audit.yml`](.github/workflows/contract-audit.yml), which uploads the
results to the repository's Security tab. Run the same audit yourself:

```bash
java -jar DhrLang-4.0.2.jar --audit input/contracts/ERC20Token.dhr
```

---

## Troubleshooting

**"No @contract classes found"**
Ã¢â€ â€™ Add `@contract` annotation above your class: `@contract class MyToken { ... }`

**"Wallet error: Environment variable DHRLANG_PRIVATE_KEY is not set"**
Ã¢â€ â€™ Set your key: `export DHRLANG_PRIVATE_KEY=0x...` or use `contract wallet create`

**"Unknown network"**
Ã¢â€ â€™ Run `contract networks` to see valid names. Use `--network=sepolia` format.

**"Verification failed"**
Ã¢â€ â€™ Ensure `DHRLANG_ETHERSCAN_API_KEY` is set. Get a free key at https://etherscan.io/apis
