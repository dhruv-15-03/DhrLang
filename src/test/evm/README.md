# Independent EVM conformance

This suite runs DhrLang-generated creation/runtime bytecode on the pinned
EthereumJS VM. It does not use DhrLang's debugger or spec evaluator as its oracle.
No RPC endpoint, funded wallet, service account or real transaction is involved.

From the repository root:

```powershell
.\gradlew.bat stageCompiler
Set-Location src\test\evm
npm ci
npm test
```

Use `./gradlew` on Linux/macOS. Node.js 22+ and Java 17+ are required.
`DHRLANG_TEST_JAR` can point to a downloaded compiler; `DHRLANG_TEST_JAVA` can
select its Java executable. The CI job tests the downloaded build artifact.

Tests assert that creation installs exactly the compiled runtime, then execute
real calls and inspect values, state changes and reverts. Failure cases must
reject unsupported input without writing a deployable artifact.

This is a limited regression corpus, not production certification. See
`BLOCKCHAIN_TUTORIAL.md` for the target's remaining limitations.
