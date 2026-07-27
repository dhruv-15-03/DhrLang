# DhrLang Production Roadmap
## From Alpha to Enterprise-Grade Blockchain Language

> **Version**: 1.0 | **Status**: Active | **Last Updated**: April 12, 2026
> **Goal**: Make DhrLang the safest, simplest smart contract language — production-ready for enterprise blockchain deployment.

---

## Current State Assessment

### What's REAL (Verified, Tested, Working)
| Component | Evidence |
|-----------|---------|
| Keccak-256 | Matches `c5d246...` test vector, correct ERC-20 selectors |
| secp256k1 ECDSA | Derives Anvil addresses correctly (0xf39F..., 0x7099...) |
| RFC 6979 deterministic k | Signing is deterministic + low-S (EIP-2) |
| ABI encoder/decoder | Solidity-compatible, tested against standard selectors |
| EVM opcodes (150+) | Full opcode set with gas costs |
| Storage layout | Deterministic slot assignment, packing optimization |
| Safety analysis | Reentrancy, checks-effects-interactions, view/pure enforcement |
| RLP encoding | Correct Ethereum RLP for transactions |
| JSON-RPC client | `eth_sendRawTransaction`, `eth_getTransactionReceipt`, etc. |
| Contract templates | ERC-20, ERC-721, MultiSig, Staking Vault |
| **1,168 tests** | **0 failures** across all modules |

### What's Missing for Production
| Gap | Severity | Phase |
|-----|----------|-------|
| No testnet deployment proof | Critical | Phase 2 |
| No mapping/balance storage in EVM | Critical | Phase 2 |
| Gas optimizer not competitive with solc | High | Phase 3 |
| No formal verification | High | Phase 4 |
| No contract upgrade patterns (proxy) | Medium | Phase 3 |
| No event indexing / subgraph support | Medium | Phase 4 |
| No EIP-712 typed data signing | Medium | Phase 3 |
| No third-party security audit | Critical | Phase 5 |

---

## Phase 2: Testnet Proof (Weeks 1-3)
> **Goal**: Deploy a real ERC-20 token to Sepolia and interact with it on Etherscan.

### 2.1 EVM Code Generation Completeness
- [ ] **Mapping storage**: Implement `mapping(Address => uint256)` as Keccak-256 slot hashing
- [ ] **Balance tracking**: ERC-20 `balanceOf()` reads from mapping storage slots
- [ ] **Transfer logic**: Real token transfer with balance checks in generated EVM
- [ ] **Event emission**: Generate LOG0-LOG4 opcodes for `@event` functions
- [ ] **Constructor args**: ABI-encode constructor parameters in creation bytecode

### 2.2 Anvil E2E Test
- [ ] Start Anvil in CI (GitHub Actions)
- [ ] Compile `ERC20Token.dhr` → EVM bytecode
- [ ] Deploy to Anvil via `EthJsonRpcClient`
- [ ] Call `getName()`, `getTotalSupply()` — verify return values
- [ ] Call `mint()` — verify state change
- [ ] Call `transfer()` — verify balance updates
- [ ] All automated in `AnvilIntegrationTest.java`

### 2.3 Sepolia Deployment
- [ ] Deploy ERC-20 from DhrLang to Sepolia testnet
- [ ] Verify on Etherscan (source code verification)
- [ ] Document with screenshots in `BLOCKCHAIN_TUTORIAL.md`
- [ ] Record tx hash + contract address as proof

### Exit Criteria
```
✓ ERC-20 deployed to Sepolia via `dhrlang contract deploy --network=sepolia`
✓ Contract verified on Etherscan
✓ balanceOf(), transfer(), mint() all work on-chain
✓ CI runs Anvil integration tests on every push
```

---

## Phase 3: Feature Parity with Solidity (Weeks 4-8)
> **Goal**: Support the 20 most common Solidity patterns.

### 3.1 Storage Patterns
- [ ] `mapping(K => V)` with nested mappings
- [ ] Dynamic arrays in storage
- [ ] Struct packing in storage slots
- [ ] Storage slot collision detection

### 3.2 Contract Patterns
- [ ] **Proxy pattern**: UUPS and Transparent proxy for upgradeable contracts
- [ ] **Factory pattern**: Contract deploying other contracts (CREATE2)
- [ ] **Interface compliance**: Verify `implements` matches function signatures
- [ ] **Inheritance linearization**: C3 linearization for diamond inheritance

### 3.3 Signing & Interaction
- [ ] EIP-712 typed data signing (permits, meta-tx)
- [ ] EIP-2612 permit support
- [ ] Multi-call batching (batch multiple calls in one tx)
- [ ] Ethers.js / Viem compatible ABI output

### 3.4 Gas Optimization
- [ ] Stack scheduling (minimize DUP/SWAP depth)
- [ ] Function inlining for small pure functions
- [ ] Storage read caching (SLOAD once, reuse from stack)
- [ ] Dead code elimination at EVM level
- [ ] **Benchmark**: Compare gas costs vs `solc -O2` for 10 standard contracts

### Exit Criteria
```
✓ mapping(address => uint256) works in compiled contracts
✓ Proxy upgrade pattern generates correct delegatecall bytecode
✓ Gas within 1.5x of equivalent Solidity for ERC-20/721
✓ EIP-712 signing produces Etherscan-compatible signatures
```

---

## Phase 4: Safety Moat — DhrLang's Competitive Edge (Weeks 9-14)
> **Goal**: Build the features that Solidity CAN'T have — making DhrLang the safest choice.

### 4.1 Formal Verification (Z3 Integration)
- [ ] Encode contract invariants as SMT constraints
- [ ] `@invariant totalSupply >= 0` annotation → Z3 check at compile time
- [ ] Arithmetic overflow detection via symbolic execution
- [ ] Access control verification (only `owner` can call `mint`)
- [ ] Loop bound analysis (prevent gas limit DoS)

### 4.2 Compiler-Level Safety (Already Ahead of Solidity)
| Feature | Solidity | DhrLang |
|---------|----------|---------|
| Reentrancy prevention | Library (opt-in) | **Compiler-enforced** `@nonreentrant` |
| CEI pattern | Convention | **Compiler-enforced** (EffectOrderingAnalyzer) |
| View/pure violations | Compiler check | **Compiler check** ✓ |
| Integer overflow | Runtime revert (0.8+) | **Compile-time detection** (planned) |
| tx.origin phishing | Docs warn | **Language doesn't expose tx.origin** |
| Unchecked blocks | `unchecked {}` escape hatch | **No escape hatch** — always safe |

### 4.3 Advanced Static Analysis
- [ ] Taint tracking (user input → state write path analysis)
- [ ] Privilege escalation detection (who can call what)
- [ ] Flash loan attack pattern detection
- [ ] Price oracle manipulation detection
- [ ] Symbolic execution for assertion violations

### 4.4 Gas Limit Safety
- [ ] Automatic loop bound detection → warn if potentially unbounded
- [ ] Storage key enumeration warnings
- [ ] Calldata size limits enforcement
- [ ] Maximum gas consumption estimation per function

### Exit Criteria
```
✓ @invariant annotation verified by Z3 at compile time
✓ Symbolic execution catches all of SWC-100 through SWC-136
✓ Zero false positives on a corpus of 50 known-vulnerable contracts
✓ DhrLang catches 95%+ of vulnerabilities that led to >$1M losses
```

---

## Phase 5: Production Certification (Weeks 15-20)
> **Goal**: External validation that DhrLang is safe for real money.

### 5.1 Security Audit
- [ ] Commission audit from Trail of Bits, OpenZeppelin, or Consensys Diligence
- [ ] Scope: compiler code generation, crypto implementation, standard library
- [ ] Fix all critical/high findings
- [ ] Publish audit report publicly

### 5.2 Bug Bounty Program
- [ ] Launch on Immunefi
- [ ] $10K–$100K bounties for compiler bugs that produce vulnerable bytecode
- [ ] $5K–$50K for crypto implementation vulnerabilities

### 5.3 Certification & Compliance
- [ ] Gas cost accuracy certification (compare vs geth EVM)
- [ ] ABI compatibility certification (fuzz test against Solidity ABI)
- [ ] Bytecode equivalence testing vs known-good Solidity contracts

### 5.4 Documentation & Developer Experience
- [ ] Complete language specification (SPEC.md v3)
- [ ] 20+ tutorial examples with gas cost breakdowns
- [ ] Video tutorial series: "DhrLang in 30 Minutes"
- [ ] Interactive documentation website with live playground
- [ ] "Solidity → DhrLang" migration guide

### Exit Criteria
```
✓ External audit completed with no critical findings
✓ Bug bounty live with $100K+ pool
✓ 10+ independent developers have deployed contracts
✓ At least 1 DeFi protocol considering DhrLang adoption
```

---

## Phase 6: Ecosystem & Adoption (Months 6-12)
> **Goal**: Build the developer ecosystem.

### 6.1 Developer Tooling
- [ ] Foundry plugin (`forge build --lang dhrlang`)
- [ ] Hardhat plugin
- [ ] Remix IDE integration
- [ ] VS Code language server (full LSP)
- [ ] Contract size optimizer

### 6.2 Standard Library Expansion
- [ ] Governance (Governor + TimelockController)
- [ ] Token extensions (ERC-2612 permit, ERC-4626 vault)
- [ ] Oracle integration patterns (Chainlink, Pyth)
- [ ] Cross-chain messaging (LayerZero, Axelar)
- [ ] Flash loan-safe patterns

### 6.3 Community
- [ ] Open-source contributor guide
- [ ] Discord/Telegram community
- [ ] Weekly office hours
- [ ] Hackathon sponsorships
- [ ] University partnerships

---

## Success Metrics

| Milestone | Metric | Target Date |
|-----------|--------|------------|
| Phase 2 complete | First contract deployed to Sepolia | Week 3 |
| Phase 3 complete | 20 Solidity patterns supported | Week 8 |
| Phase 4 complete | Z3 formal verification working | Week 14 |
| Phase 5 complete | External audit passed | Week 20 |
| First production deployment | Real money on mainnet | Month 8 |
| 100 developers | Active GitHub contributors + users | Month 12 |

---

## Risk Register

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| EVM bytecode generates wrong behavior | Critical | Medium | Extensive parity tests vs Solidity output |
| Crypto implementation vulnerability | Critical | Low | BouncyCastle (battle-tested), audit |
| Gas costs too high vs Solidity | High | Medium | Optimization phase, benchmarking |
| No developer adoption | High | High | Start with educational use case, build up |
| Breaking language changes | Medium | Medium | Semantic versioning, migration tooling |

---

## Architecture Decision Records

### ADR-001: BouncyCastle for secp256k1
**Decision**: Use `bcprov-jdk18on:1.78.1` for all elliptic curve operations.
**Rationale**: Battle-tested Java crypto library (20+ years). No custom EC math.
**Trade-off**: Adds ~5MB to fat JAR. Acceptable for security.

### ADR-002: Built-in Keccak-256
**Decision**: Keep hand-written Keccak-256 in `FunctionSelector.java`.
**Rationale**: 100% correct (test vector verified), zero dependencies, ~200 lines.
**Risk**: Low — algorithm is well-specified, immutable, and thoroughly tested.

### ADR-003: Safety by Default (No Escape Hatch)
**Decision**: DhrLang will NOT add `unchecked {}` or similar escape hatches.
**Rationale**: The $3.8B in annual smart contract losses proves that escape hatches get abused. DhrLang's value proposition is "safety the developer can't opt out of."
**Trade-off**: Some gas-optimized patterns impossible. Worth it for security.

### ADR-004: Compile-Time Reentrancy Prevention
**Decision**: `@nonreentrant` is a compile-time annotation, not a runtime guard.
**Rationale**: Runtime guards (like OpenZeppelin's) cost gas and can be forgotten. DhrLang's `NonReentrantChecker` runs at compile time — zero gas cost, impossible to forget.

---

## Version Targets

| Version | Codename | Key Feature | Tests |
|---------|----------|-------------|-------|
| 2.0.0 | Released | EVM backend + AI agents | 1,168 |
| 3.0.0 | *Current* | SafeMath, access control, LSP, optimizer, labeled loops, as cast | 1,287 |
| 3.1.0 | **Testnet** | First real deployment proof | ~1,400 |
| 3.2.0 | **Parity** | Solidity feature parity (top 20) | ~1,600 |
| 4.0.0 | **Ecosystem** | Module system, lambdas, enums, REPL, package manager | ~2,000 |
