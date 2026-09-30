const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const path = require('node:path');
const JSZip = require('jszip');

const extensionDir = path.resolve(__dirname, '..');
const releaseDir = path.resolve(extensionDir, '..', 'build', 'release');
const publisher = 'EnggWithDhruv';

function digest(bytes) {
    return crypto.createHash('sha256').update(bytes).digest('hex');
}

async function requiredEntry(zip, name) {
    const entry = zip.file(name);
    assert.ok(entry, `Missing packaged file: ${name}`);
    return entry.async('nodebuffer');
}

async function verifyCompiler(bytes, version) {
    const jar = await JSZip.loadAsync(bytes);
    const manifest = (await requiredEntry(jar, 'META-INF/MANIFEST.MF')).toString('utf8');
    assert.match(manifest, /^Main-Class: dhrlang\.Main\r?$/m, 'Wrong compiler entry point');
    const actualVersion = /^Implementation-Version: (.+)\r?$/m.exec(manifest);
    assert.equal(actualVersion && actualVersion[1].trim(), version, 'Compiler version mismatch');
    for (const name of [
        'dhrlang/Main.class',
        'dhrlang/deploy/WalletManager.class',
        'org/bouncycastle/crypto/Digest.class',
        'org/apache/commons/lang3/StringUtils.class',
        'com/fasterxml/jackson/databind/ObjectMapper.class'
    ]) {
        await requiredEntry(jar, name);
    }
}

async function verifyPackage(bytes, compiler, expected) {
    await verifyCompiler(compiler, expected.version);
    const zip = await JSZip.loadAsync(bytes);
    const metadata = JSON.parse((await requiredEntry(zip, 'extension/package.json')).toString('utf8'));
    assert.equal(metadata.publisher, publisher, 'Unexpected extension publisher');
    assert.equal(metadata.name, expected.name, 'Extension name mismatch');
    assert.equal(metadata.version, expected.version, 'Extension version mismatch');
    assert.equal(metadata.main, './out/extension.js', 'Unexpected extension entry point');
    await requiredEntry(zip, 'extension/out/extension.js');
    await requiredEntry(zip, 'extension/readme.md');
    await requiredEntry(zip, 'extension/LICENSE.txt');
    assert.equal(metadata.icon, expected.icon, 'Extension icon mismatch');
    await requiredEntry(zip, `extension/${metadata.icon}`);
    await requiredEntry(zip, 'extension/node_modules/vscode-languageclient/package.json');
    const bundled = await requiredEntry(zip, 'extension/compiler/DhrLang.jar');
    assert.equal(digest(bundled), digest(compiler), 'VSIX compiler differs from the standalone JAR');
}

async function verifyRelease(directory, expected, expectedRevision) {
    const manifest = JSON.parse(await fs.readFile(path.join(directory, 'release-manifest.json'), 'utf8'));
    assert.equal(manifest.schemaVersion, 1, 'Unknown artifact manifest schema');
    assert.equal(manifest.version, expected.version, 'Release version mismatch');
    if (expectedRevision) {
        assert.equal(manifest.sourceRevision, expectedRevision, 'Compiler and extension tags must identify the same commit');
    }
    const jarName = 'DhrLang.jar';
    const vsixName = `${expected.name}-${expected.version}.vsix`;
    assert.deepEqual(Object.keys(manifest.sha256).sort(), [jarName, vsixName].sort(),
        'Manifest must identify the exact compiler and extension artifacts');
    const compiler = await fs.readFile(path.join(directory, jarName));
    const vsix = await fs.readFile(path.join(directory, vsixName));
    assert.equal(digest(compiler), manifest.sha256[jarName], 'Standalone compiler checksum mismatch');
    assert.equal(digest(vsix), manifest.sha256[vsixName], 'VSIX checksum mismatch');
    await verifyPackage(vsix, compiler, expected);
}

async function main() {
    const metadata = JSON.parse(await fs.readFile(path.join(extensionDir, 'package.json'), 'utf8'));
    if (process.argv[2] === 'verify') {
        const directory = path.resolve(process.argv[3] || releaseDir);
        await verifyRelease(directory, metadata, process.env.RELEASE_COMMIT || process.env.GITHUB_SHA);
        console.log(`Verified compiler and VSIX: ${directory}`);
        return;
    }
    assert.equal(process.argv.length, 2, 'Usage: package.cjs [verify [directory]]');
    assert.equal(metadata.publisher, publisher, 'Unexpected extension publisher');
    const stagedJar = path.resolve(extensionDir, '..', 'build', 'compiler', 'DhrLang.jar');
    const compiler = await fs.readFile(stagedJar);
    await verifyCompiler(compiler, metadata.version);
    await fs.mkdir(path.join(extensionDir, 'compiler'), { recursive: true });
    await fs.writeFile(path.join(extensionDir, 'compiler', 'DhrLang.jar'), compiler);
    await fs.mkdir(releaseDir, { recursive: true });
    const vsixName = `${metadata.name}-${metadata.version}.vsix`;
    const vsixPath = path.join(releaseDir, vsixName);
    const { createVSIX } = require('@vscode/vsce');
    await createVSIX({ cwd: extensionDir, packagePath: vsixPath, useYarn: false });
    const vsix = await fs.readFile(vsixPath);
    await verifyPackage(vsix, compiler, metadata);
    await fs.writeFile(path.join(releaseDir, 'DhrLang.jar'), compiler);
    await fs.writeFile(path.join(releaseDir, 'release-manifest.json'), JSON.stringify({
        schemaVersion: 1,
        version: metadata.version,
        sourceRevision: process.env.RELEASE_COMMIT || process.env.GITHUB_SHA || null,
        sha256: { 'DhrLang.jar': digest(compiler), [vsixName]: digest(vsix) }
    }, null, 2) + '\n');
    await verifyRelease(releaseDir, metadata, process.env.RELEASE_COMMIT || process.env.GITHUB_SHA);
    console.log(`Verified release artifacts: ${releaseDir}`);
}

module.exports = { digest, verifyCompiler, verifyPackage, verifyRelease };

if (require.main === module) {
    main().catch(error => {
        console.error(`Packaging failed: ${error.message}`);
        process.exitCode = 1;
    });
}
