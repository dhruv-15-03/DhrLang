# DhrLang Blockchain Tutorial: Write â†’ Compile â†’ Deploy â†’ Verify

> **End-to-end guide** for building, deploying, and verifying smart contracts using DhrLang.

---

## Prerequisites

- Java 17+ installed
- DhrLang 3.0.0 JAR (`DhrLang-3.0.0.jar`)
- (Optional) Foundry toolkit for local testing: https://book.getfoundry.sh/

```bash
java -jar DhrLang-3.0.0.jar --version
# â†’ DhrLang version 3.0.0
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
    @storage Address owner;

    @constructor
    kaam init(sab _name, sab _symbol, num _initialSupply) {
        name = _name;
        symbol = _symbol;
        owner = msg.sender;
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
- `@contract` — marks the class as a smart contract
- `@storage` — fields persisted on-chain (EVM storage slots)
- `@constructor` — runs once at deployment
- `@view` — read-only (no gas for external calls)
- `@nonreentrant` — compiler enforces reentrancy protection
- `@event` — emits EVM log events; mark params `indexed` to make them filterable topics
- `@error` — declares a gas-efficient custom error; raise it with `revert(ErrName(args))`
  or `require(cond, ErrName(args))`
- `@checked` / `@unchecked` — select overflow behaviour for `+`, `-`, `*` on `num` in a method.
  `@checked` reverts on overflow/underflow; `@unchecked` wraps modulo 2²⁵⁶. (Alpha default is
  wrapping; this becomes checked-by-default in the v4.0.0 beta.)
- `@requires(expr)` / `@ensures(expr)` — design-by-contract pre/postconditions on a method.
  `@requires` is checked at entry (reverts `precondition failed`); `@ensures` is checked at
  every return (reverts `postcondition failed`) and may reference `result`, the return value.
- `@invariant(expr)` — a contract-level invariant (declared next to `@contract`), re-checked
  after every state-mutating method and reverting `invariant violated`.

### Arithmetic safety example

```dhrlang
@contract
class Vault {
    @storage num balance;

    // Reverts with "arithmetic overflow" / "arithmetic underflow" instead of wrapping.
    @checked
    kaam deposit(num amount) {
        balance = balance + amount;
    }

    // Opt out for hot paths where wrapping is intentional.
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
> with **DHR-E516** — on the EVM an unresolved identifier would silently compile to `0`.

### Fuzz your specs (`contract fuzz`)

Specs are only useful if they actually hold. The fuzzer searches for an input that
*falsifies* an `@ensures` or `@invariant`, so you can catch a logic bug before you deploy:

```bash
java -jar DhrLang-3.6.0.jar contract fuzz MyToken.dhr
# reproducible run, more iterations:
java -jar DhrLang-3.6.0.jar contract fuzz --runs=512 --seed=42 MyToken.dhr
```

It runs each function over a simulated EVM state (uint256 wrapping arithmetic, `@checked`
overflow reverts, storage and mappings default to `0`) and prints a per-function tally.
If a spec is violated it reports a **minimized counterexample**:

```
  Buggy::set — 256 runs: 0 ok, 256 violations, 0 reverts, 0 skipped, 0 errors

Failing inputs:
  ✗ Buggy::set(0, 0) → invariant violated: @invariant(total == a + b)

Result: ISSUES FOUND ✗
```

The fuzzer is **sound, not complete**: it only flags a violation on a faithful execution.
Inputs that fail a `@requires` precondition are reported as *skipped* (out of scope), and
anything it cannot model faithfully (e.g. a call into another function) is skipped rather
than reported as a false positive. The command exits non-zero when a counterexample is
found, so `contract fuzz` works as a CI gate.

---

## Start From a Standard-Library Template

Don't write boilerplate from scratch. DhrLang ships eight audited-pattern base contracts
you can browse and scaffold straight from the CLI:

```bash
# Browse the catalog:
java -jar DhrLang-3.8.0.jar contract stdlib list

# Read a template's source:
java -jar DhrLang-3.8.0.jar contract stdlib show ERC20

# Scaffold a ready-to-edit file (optionally renaming the contract):
java -jar DhrLang-3.8.0.jar contract stdlib new Ownable --name=MyToken --output=input/contracts
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
java -jar DhrLang-3.0.0.jar contract compile MyToken.dhr
```

Output:
```
1 contract(s) compiled â†’ /path/to/build/evm
```

Generated artifacts in `build/evm/`:
- `MyToken.bin` â€” creation bytecode (deployed to chain)
- `MyToken.runtime.bin` â€” runtime bytecode (stored on-chain)
- `MyToken.abi.json` â€” ABI for tools (ethers.js, Foundry, etc.)

---

## Step 3: Estimate Gas Costs

```bash
java -jar DhrLang-3.0.0.jar contract gas MyToken.dhr
```

Output:
```
â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
â•‘                    GAS ESTIMATION REPORT                    â•‘
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
â•‘  Bytecode size:              342 bytes                      â•‘
â•‘  Storage slots:                4                            â•‘
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
â•‘  Gas Breakdown:                                             â•‘
â•‘    Intrinsic (tx base):        21,000 gas                   â•‘
â•‘    Calldata:                    4,872 gas                   â•‘
â•‘    Contract creation:          32,000 gas                   â•‘
â•‘    Code deposit:               68,400 gas                   â•‘
â•‘    Constructor execution:      90,000 gas                   â•‘
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
â•‘  TOTAL ESTIMATED GAS:         216,272                       â•‘
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
â•‘  Cost at 30 gwei:            0.006488 ETH (~$16.22 @ $2500) â•‘
â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
```

For JSON output (CI/tools):
```bash
java -jar DhrLang-3.0.0.jar contract gas --json MyToken.dhr
```

---

## Step 4: Test Locally (Anvil/Hardhat)

Start a local Ethereum node:
```bash
# Using Foundry's Anvil
anvil
# â†’ Listening on 127.0.0.1:8545
# â†’ Private Key: 0xac0974bec...
```

Deploy locally:
```bash
java -jar DhrLang-3.0.0.jar contract deploy --network=local MyToken.dhr
```

Or generate a deploy script:
```bash
java -jar DhrLang-3.0.0.jar contract deploy --network=local --dry-run --deploy-format=ethers MyToken.dhr
```

This generates `build/evm/Deploy.deploy.js` â€” run it with:
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
java -jar DhrLang-3.0.0.jar contract wallet create
# Enter private key (hex): ****
# Enter keystore password: ****
# Confirm password: ****
# â†’ Keystore created at ~/.dhrlang/keystore.enc
```

Show your wallet address:
```bash
java -jar DhrLang-3.0.0.jar contract wallet show
# â†’ Address: 0x1234...abcd
# â†’ Key source: KEYSTORE_FILE
```

---

## Step 6: Deploy to Testnet (Sepolia)

```bash
# Get Sepolia ETH from a faucet: https://sepoliafaucet.com

java -jar DhrLang-3.0.0.jar contract deploy --network=sepolia MyToken.dhr
```

Output:
```
â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
â•‘              DhrLang Contract Deployment                     â•‘
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
  Network:    Sepolia Testnet (chainId: 11155111)
  Contracts:  1
  MyToken: ~216,272 gas
  Deployer:   0x1234...abcd
  Key source: ENVIRONMENT_VARIABLE
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
  Building tx for MyToken...
  Signed: 0x02f9...
  Raw signed tx written to build/evm/MyToken.signed.tx
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
  Next steps:
    1. Broadcast: cast send --raw <signed.tx> --rpc-url https://sepolia.infura.io/v3/{API_KEY}
    2. Verify:    dhrlang contract verify --address=<deployed> --network=sepolia MyToken.dhr
â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
```

Broadcast using Foundry:
```bash
cast publish --rpc-url https://sepolia.infura.io/v3/YOUR_KEY \
  "$(cat build/evm/MyToken.signed.tx)"
```

---

## Step 7: Verify on Etherscan

```bash
export DHRLANG_ETHERSCAN_API_KEY=your_api_key

java -jar DhrLang-3.0.0.jar contract verify \
    --address=0xDeployedContractAddress \
    --network=sepolia \
    MyToken.dhr
```

Output:
```
Verifying MyToken at 0xDeployed... on Sepolia Testnet...
  âœ“ Contract verified successfully!
  View: https://sepolia.etherscan.io/address/0xDeployed#code
```

---

## Step 8: Security Audit

Run the built-in security auditor before deploying to mainnet:

```bash
java -jar DhrLang-3.6.0.jar --audit MyToken.dhr
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
java -jar DhrLang-3.6.0.jar contract safety MyToken.dhr
# report-only (don't fail the build):
java -jar DhrLang-3.6.0.jar contract safety --fail-on=none MyToken.dhr
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
java -jar DhrLang-3.0.0.jar contract deploy --network=mainnet --dry-run MyToken.dhr

# Real deployment
java -jar DhrLang-3.0.0.jar contract deploy --network=mainnet MyToken.dhr
```

---

## Step 10: Export for Hardhat / Foundry / viem

Already have a JavaScript/TypeScript or Solidity-tooling project? `contract export`
projects every `@contract` into the artifact shapes those toolchains already consume,
so a DhrLang contract drops in without hand-copying ABIs or bytecode:

```bash
# Emit all three targets into build/contracts/ (default):
java -jar DhrLang-3.7.0.jar contract export MyToken.dhr

# Pick a single target and destination:
java -jar DhrLang-3.7.0.jar contract export --format=ts --output=app/src/contracts MyToken.dhr
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

## Supported Networks

```bash
java -jar DhrLang-3.0.0.jar contract networks
```

| Network | Chain ID | Type | CLI Name |
|---------|----------|------|----------|
| Ethereum Mainnet | 1 | L1 | `mainnet` |
| Sepolia Testnet | 11155111 | Testnet | `sepolia` |
| Arbitrum One | 42161 | L2 | `arbitrum` |
| Base | 8453 | L2 | `base` |
| Optimism | 10 | L2 | `optimism` |
| Polygon | 137 | Sidechain | `polygon` |
| Local (Anvil) | 31337 | Local | `local` |

---

## Quick Reference

| Command | Description |
|---------|-------------|
| `contract compile <file>` | Compile to EVM bytecode + ABI |
| `contract stdlib <list\|show\|new> [Name]` | Browse & scaffold standard base contracts |
| `contract gas <file>` | Gas cost estimation report |
| `contract fuzz [--runs=N] [--seed=N] <file>` | Property-fuzz `@ensures`/`@invariant` specs |
| `contract safety [--fail-on=<sev>] <file>` | Audit + fuzz -> safety score, grade, SARIF; CI gate |
| `contract export [--format=<fmt>] [--output=<dir>] <file>` | Emit Hardhat / Foundry / viem artifacts |
| `contract deploy --network=<net> <file>` | Build + sign + deploy |
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

Ready-to-use contracts in `input/contracts/`:

| File | Description |
|------|-------------|
| `ERC20Token.dhr` | Standard fungible token (ERC-20) |
| `ERC721NFT.dhr` | Non-fungible token (ERC-721) |
| `MultiSigWallet.dhr` | M-of-N multi-signature wallet |
| `StakingVault.dhr` | Token staking with rewards |

---

## Troubleshooting

**"No @contract classes found"**
â†’ Add `@contract` annotation above your class: `@contract class MyToken { ... }`

**"Wallet error: Environment variable DHRLANG_PRIVATE_KEY is not set"**
â†’ Set your key: `export DHRLANG_PRIVATE_KEY=0x...` or use `contract wallet create`

**"Unknown network"**
â†’ Run `contract networks` to see valid names. Use `--network=sepolia` format.

**"Verification failed"**
â†’ Ensure `DHRLANG_ETHERSCAN_API_KEY` is set. Get a free key at https://etherscan.io/apis
