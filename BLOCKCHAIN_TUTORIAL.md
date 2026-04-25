# DhrLang Blockchain Tutorial: Write → Compile → Deploy → Verify

> **End-to-end guide** for building, deploying, and verifying smart contracts using DhrLang.

---

## Prerequisites

- Java 17+ installed
- DhrLang 2.0.0 JAR (`DhrLang-2.0.0.jar`)
- (Optional) Foundry toolkit for local testing: https://book.getfoundry.sh/

```bash
java -jar DhrLang-2.0.0.jar --version
# → DhrLang version 2.0.0
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
        if (amount <= 0) {
            throw "Amount must be positive";
        }
        totalSupply = totalSupply;
    }

    @event
    kaam Transfer(Address from, Address to, num amount) {}
}
```

Key annotations:
- `@contract` — marks the class as a smart contract
- `@storage` — fields persisted on-chain (EVM storage slots)
- `@constructor` — runs once at deployment
- `@view` — read-only (no gas for external calls)
- `@nonreentrant` — compiler enforces reentrancy protection
- `@event` — emits EVM log events

---

## Step 2: Compile to EVM Bytecode

```bash
java -jar DhrLang-2.0.0.jar contract compile MyToken.dhr
```

Output:
```
1 contract(s) compiled → /path/to/build/evm
```

Generated artifacts in `build/evm/`:
- `MyToken.bin` — creation bytecode (deployed to chain)
- `MyToken.runtime.bin` — runtime bytecode (stored on-chain)
- `MyToken.abi.json` — ABI for tools (ethers.js, Foundry, etc.)

---

## Step 3: Estimate Gas Costs

```bash
java -jar DhrLang-2.0.0.jar contract gas MyToken.dhr
```

Output:
```
╔══════════════════════════════════════════════════════════════╗
║                    GAS ESTIMATION REPORT                    ║
╠══════════════════════════════════════════════════════════════╣
║  Bytecode size:              342 bytes                      ║
║  Storage slots:                4                            ║
╠══════════════════════════════════════════════════════════════╣
║  Gas Breakdown:                                             ║
║    Intrinsic (tx base):        21,000 gas                   ║
║    Calldata:                    4,872 gas                   ║
║    Contract creation:          32,000 gas                   ║
║    Code deposit:               68,400 gas                   ║
║    Constructor execution:      90,000 gas                   ║
╠══════════════════════════════════════════════════════════════╣
║  TOTAL ESTIMATED GAS:         216,272                       ║
╠══════════════════════════════════════════════════════════════╣
║  Cost at 30 gwei:            0.006488 ETH (~$16.22 @ $2500) ║
╚══════════════════════════════════════════════════════════════╝
```

For JSON output (CI/tools):
```bash
java -jar DhrLang-2.0.0.jar contract gas --json MyToken.dhr
```

---

## Step 4: Test Locally (Anvil/Hardhat)

Start a local Ethereum node:
```bash
# Using Foundry's Anvil
anvil
# → Listening on 127.0.0.1:8545
# → Private Key: 0xac0974bec...
```

Deploy locally:
```bash
java -jar DhrLang-2.0.0.jar contract deploy --network=local MyToken.dhr
```

Or generate a deploy script:
```bash
java -jar DhrLang-2.0.0.jar contract deploy --network=local --dry-run --deploy-format=ethers MyToken.dhr
```

This generates `build/evm/Deploy.deploy.js` — run it with:
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
java -jar DhrLang-2.0.0.jar contract wallet create
# Enter private key (hex): ****
# Enter keystore password: ****
# Confirm password: ****
# → Keystore created at ~/.dhrlang/keystore.enc
```

Show your wallet address:
```bash
java -jar DhrLang-2.0.0.jar contract wallet show
# → Address: 0x1234...abcd
# → Key source: KEYSTORE_FILE
```

---

## Step 6: Deploy to Testnet (Sepolia)

```bash
# Get Sepolia ETH from a faucet: https://sepoliafaucet.com

java -jar DhrLang-2.0.0.jar contract deploy --network=sepolia MyToken.dhr
```

Output:
```
╔══════════════════════════════════════════════════════════════╗
║              DhrLang Contract Deployment                     ║
╠══════════════════════════════════════════════════════════════╣
  Network:    Sepolia Testnet (chainId: 11155111)
  Contracts:  1
  MyToken: ~216,272 gas
  Deployer:   0x1234...abcd
  Key source: ENVIRONMENT_VARIABLE
╠══════════════════════════════════════════════════════════════╣
  Building tx for MyToken...
  Signed: 0x02f9...
  Raw signed tx written to build/evm/MyToken.signed.tx
╠══════════════════════════════════════════════════════════════╣
  Next steps:
    1. Broadcast: cast send --raw <signed.tx> --rpc-url https://sepolia.infura.io/v3/{API_KEY}
    2. Verify:    dhrlang contract verify --address=<deployed> --network=sepolia MyToken.dhr
╚══════════════════════════════════════════════════════════════╝
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

java -jar DhrLang-2.0.0.jar contract verify \
    --address=0xDeployedContractAddress \
    --network=sepolia \
    MyToken.dhr
```

Output:
```
Verifying MyToken at 0xDeployed... on Sepolia Testnet...
  ✓ Contract verified successfully!
  View: https://sepolia.etherscan.io/address/0xDeployed#code
```

---

## Step 8: Security Audit

Run the built-in security auditor before deploying to mainnet:

```bash
java -jar DhrLang-2.0.0.jar --audit MyToken.dhr
```

This checks for:
- Reentrancy vulnerabilities
- Checks-effects-interactions pattern violations
- View/pure function state access violations
- Storage layout issues
- Access control gaps

---

## Step 9: Deploy to Mainnet

When ready for production:

```bash
# Dry run first!
java -jar DhrLang-2.0.0.jar contract deploy --network=mainnet --dry-run MyToken.dhr

# Real deployment
java -jar DhrLang-2.0.0.jar contract deploy --network=mainnet MyToken.dhr
```

---

## Supported Networks

```bash
java -jar DhrLang-2.0.0.jar contract networks
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
| `contract gas <file>` | Gas cost estimation report |
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
→ Add `@contract` annotation above your class: `@contract class MyToken { ... }`

**"Wallet error: Environment variable DHRLANG_PRIVATE_KEY is not set"**
→ Set your key: `export DHRLANG_PRIVATE_KEY=0x...` or use `contract wallet create`

**"Unknown network"**
→ Run `contract networks` to see valid names. Use `--network=sepolia` format.

**"Verification failed"**
→ Ensure `DHRLANG_ETHERSCAN_API_KEY` is set. Get a free key at https://etherscan.io/apis
