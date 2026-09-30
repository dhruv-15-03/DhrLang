import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { mkdtempSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { createVM } from '@ethereumjs/vm';
import { createAccount, createAddressFromString, bytesToHex } from '@ethereumjs/util';
import { keccak256 } from 'ethereum-cryptography/keccak.js';

const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const compiler = process.env.DHRLANG_TEST_JAR
    ? path.resolve(process.env.DHRLANG_TEST_JAR)
    : path.join(repository, 'build', 'compiler', 'DhrLang.jar');
assert.ok(existsSync(compiler), 'Run stageCompiler before the independent EVM tests');
const directory = mkdtempSync(path.join(os.tmpdir(), 'dhrlang-evm-'));
after(() => rmSync(directory, { recursive: true, force: true }));
let sequence = 0;

function compile(source, failure) {
    const input = path.join(directory, `probe-${sequence++}.dhr`);
    const output = path.join(directory, `artifacts-${sequence}`);
    writeFileSync(input, source);
    const processResult = spawnSync(process.env.DHRLANG_TEST_JAVA || 'java', [
        '-Xmx128m', '-Dfile.encoding=UTF-8', '-jar', compiler,
        '--no-color', '--compile-evm', `--output=${output}`, input
    ], { encoding: 'utf8', timeout: 30000, cwd: directory });
    assert.ifError(processResult.error);
    const diagnostics = processResult.stdout + processResult.stderr;
    if (failure) {
        assert.notEqual(processResult.status, 0, 'Unsupported source was accepted');
        assert.match(diagnostics, failure);
        assert.ok(!existsSync(path.join(output, 'Probe.bin')), 'Failure must not write deployable output');
        return;
    }
    assert.equal(processResult.status, 0, diagnostics);
    return {
        creation: readFileSync(path.join(output, 'Probe.bin')),
        runtime: readFileSync(path.join(output, 'Probe.runtime.bin'))
    };
}

function word(value) {
    return Buffer.from(BigInt(value).toString(16).padStart(64, '0'), 'hex');
}

function calldata(signature, ...args) {
    return Buffer.concat([Buffer.from(keccak256(Buffer.from(signature)).slice(0, 4)), ...args.map(word)]);
}

async function deploy(artifact, ...args) {
    const vm = await createVM();
    const caller = createAddressFromString('0x1000000000000000000000000000000000000001');
    await vm.stateManager.putAccount(caller, createAccount({ balance: 10n ** 20n }));
    const result = await vm.evm.runCall({
        caller, data: Buffer.concat([artifact.creation, ...args.map(word)]), gasLimit: 10_000_000n
    });
    assert.equal(result.execResult.exceptionError, undefined, String(result.execResult.exceptionError));
    assert.ok(result.createdAddress);
    const code = await vm.stateManager.getCode(result.createdAddress);
    assert.equal(bytesToHex(code), bytesToHex(artifact.runtime),
        'Creation bytecode must install exactly the compiled runtime, not a shifted or truncated copy');
    return {
        call: async (signature, args = [], value = 0n) => vm.evm.runCall({
            to: result.createdAddress, caller,
            data: signature ? calldata(signature, ...args) : new Uint8Array(),
            value, gasLimit: 10_000_000n
        })
    };
}

function returned(result) {
    assert.equal(result.execResult.exceptionError, undefined, String(result.execResult.exceptionError));
    assert.equal(result.execResult.returnValue.length, 32);
    return BigInt(bytesToHex(result.execResult.returnValue));
}

test('creation and dispatch preserve integer values beyond 32 bits', async () => {
    const values = [0n, 1n, 2147483647n, 2147483648n, 4294967295n, 4294967296n, 4294967297n, 9223372036854775807n];
    const methods = values.map((value, index) => `@view num value${index}() { return ${value}; }`).join('\n');
    const instance = await deploy(compile(`@contract class Probe {
        @constructor kaam init() {}
        ${methods}
    }`));
    for (const [index, expected] of values.entries()) {
        assert.equal(returned(await instance.call(`value${index}()`)), expected);
    }
});

test('constructor arguments are read from appended initcode, not calldata', async () => {
    const artifact = compile(`@contract class Probe {
        @storage num balance;
        @constructor kaam init(num initial) { balance = initial; }
        @view num value() { return balance; }
    }`);
    const instance = await deploy(artifact, 4294967297n);
    assert.equal(returned(await instance.call('value()')), 4294967297n);
});

test('throw guards reject bad input and preserve state across a call sequence', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @storage num balance;
        @constructor kaam init() { balance = 10; }
        @view num value() { return balance; }
        kaam withdraw(num amount) {
            if (amount > balance) { throw "Insufficient balance"; }
            balance = balance - amount;
        }
    }`));
    assert.equal(returned(await instance.call('value()')), 10n);
    assert.equal((await instance.call('withdraw(uint256)', [3n])).execResult.exceptionError, undefined);
    assert.equal(returned(await instance.call('value()')), 7n);
    const rejected = await instance.call('withdraw(uint256)', [8n]);
    assert.equal(rejected.execResult.exceptionError?.error, 'revert');
    assert.match(Buffer.from(rejected.execResult.returnValue).toString('utf8'), /Insufficient balance/);
    assert.equal(returned(await instance.call('value()')), 7n);
});

test('checked addition rejects overflow while unchecked addition wraps', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @constructor kaam init() {}
        @pure num add(num a, num b) { return a + b; }
        @unchecked @pure num wrap(num a, num b) { return a + b; }
    }`));
    const maximum = (1n << 256n) - 1n;
    assert.equal((await instance.call('add(uint256,uint256)', [maximum, 1n])).execResult.exceptionError?.error, 'revert');
    assert.equal(returned(await instance.call('wrap(uint256,uint256)', [maximum, 1n])), 0n);
});

test('plain receive has no function-selector word to pop', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @storage num balance;
        @constructor kaam init() { balance = 0; }
        @payable @nonreentrant kaam receive() {
            if (msg.value <= 0) { throw "Positive deposit required"; }
            balance = balance + msg.value;
        }
        @view num value() { return balance; }
    }`));
    assert.equal((await instance.call(null, [], 3n)).execResult.exceptionError, undefined);
    assert.equal(returned(await instance.call('value()')), 3n);
});

test('parameters take precedence over storage fields with the same name', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @storage num amount;
        @constructor kaam init() { amount = 7; }
        @view num echo(num amount) { return amount; }
    }`));
    assert.equal(returned(await instance.call('echo(uint256)', [12n])), 12n);
});

test('checked increment and decrement work for storage and locals', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @storage num balance;
        @constructor kaam init() { balance = 2; }
        num post() { return balance++; }
        num pre() { return --this.balance; }
        @pure num local() { num value = 5; value--; return value; }
        @view num value() { return balance; }
    }`));
    assert.equal(returned(await instance.call('post()')), 2n);
    assert.equal(returned(await instance.call('value()')), 3n);
    assert.equal(returned(await instance.call('pre()')), 2n);
    assert.equal(returned(await instance.call('local()')), 4n);
});

test('division and modulo by zero revert even in unchecked functions', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @constructor kaam init() {}
        @unchecked @pure kaam quotient(num a, num b) { return a / b; }
        @unchecked @pure num remainder(num a, num b) { return a % b; }
    }`));
    assert.equal(returned(await instance.call('quotient(uint256,uint256)', [10n, 3n])), 3n);
    assert.equal(returned(await instance.call('remainder(uint256,uint256)', [10n, 3n])), 1n);
    for (const name of ['quotient', 'remainder']) {
        assert.equal((await instance.call(`${name}(uint256,uint256)`, [1n, 0n])).execResult.exceptionError?.error, 'revert');
    }
});

test('postconditions can refer to the return value after local scope restoration', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @constructor kaam init() {}
        @ensures(result >= a) @view num value(num a) { return a; }
    }`));
    assert.equal(returned(await instance.call('value(uint256)', [9n])), 9n);
});

test('short calldata is rejected instead of inventing zero arguments', async () => {
    const instance = await deploy(compile(`@contract class Probe {
        @constructor kaam init() {}
        @view num echo(num a) { return a; }
    }`));
    assert.equal((await instance.call('echo(uint256)')).execResult.exceptionError?.error, 'revert');
});

test('an internal call is rejected instead of silently returning zero', () => {
    compile(`@contract class Probe {
        @constructor kaam init() {}
        @pure num helper() { return 7; }
        @view num value() { return helper(); }
    }`, /[Ii]nternal.*call.*not supported/);
});

test('floating-point literals are rejected instead of silently truncated', () => {
    compile(`@contract class Probe {
        @constructor kaam init() {}
        @pure duo value() { return 1.25; }
    }`, /[Ff]loating.point.*not supported/);
});
