const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const JSZip = require('jszip');
const yaml = require('js-yaml');
const { digest, verifyPackage, verifyRelease } = require('./package.cjs');

const metadata = {
    name: 'dhrlang-vscode',
    publisher: 'EnggWithDhruv',
    version: '4.0.2',
    main: './out/extension.js',
    icon: 'images/icon.png'
};

async function fixtures() {
    const jar = new JSZip();
    jar.file('META-INF/MANIFEST.MF',
        'Manifest-Version: 1.0\r\nMain-Class: dhrlang.Main\r\nImplementation-Version: 4.0.2\r\n');
    for (const file of [
        'dhrlang/Main.class', 'dhrlang/deploy/WalletManager.class',
        'org/bouncycastle/crypto/Digest.class', 'org/apache/commons/lang3/StringUtils.class',
        'com/fasterxml/jackson/databind/ObjectMapper.class'
    ]) {
        jar.file(file, 'test fixture');
    }
    const compiler = await jar.generateAsync({ type: 'nodebuffer' });
    const zip = new JSZip();
    zip.file('extension/package.json', JSON.stringify(metadata));
    for (const file of ['out/extension.js', 'readme.md', 'LICENSE.txt', 'images/icon.png',
        'node_modules/vscode-languageclient/package.json']) {
        zip.file(`extension/${file}`, 'test fixture');
    }
    zip.file('extension/compiler/DhrLang.jar', compiler);
    return { compiler, zip };
}

describe('Release package integrity', () => {
    it('accepts a matching self-contained compiler and VSIX', async () => {
        const { compiler, zip } = await fixtures();
        await verifyPackage(await zip.generateAsync({ type: 'nodebuffer' }), compiler, metadata);
    });

    describe('Release workflow gates', () => {
        async function workflow(name) {
            return yaml.load(await fs.readFile(
                path.resolve(__dirname, '..', '..', '.github', 'workflows', name), 'utf8'));
        }

        function allSteps(config) {
            return Object.values(config.jobs).flatMap(job => job.steps || []);
        }

        it('uploads quality badges without authoring repository commits', async () => {
            const quality = await workflow('bench-and-coverage.yml');
            assert.equal(quality.permissions.contents, 'read');
            assert.ok(allSteps(quality).some(step => step.with?.name === 'quality-badges'));
            assert.ok(!allSteps(quality).some(step => /git\s+(?:commit|push)/.test(step.run || '')));
        });

        it('has a single compiler-release publisher and runs the full CI check', async () => {
            const ci = await workflow('ci.yml');
            assert.ok(!allSteps(ci).some(step => step.uses?.startsWith('softprops/action-gh-release')));
            assert.ok(ci.jobs.test.steps.some(step => step.with?.arguments?.startsWith('check ')));
            const release = await workflow('release.yml');
            assert.equal(allSteps(release).filter(step => step.uses?.startsWith('softprops/action-gh-release')).length, 1);
            assert.ok(allSteps(release).some(step => step.run?.includes('verifyCompilerArtifact -PcompilerJar=build/downloaded/DhrLang.jar')));
        });

        it('configures Java and Node on the correct setup actions', async () => {
            for (const file of ['ci.yml', 'release.yml', 'vscode-extension.yml']) {
                for (const step of allSteps(await workflow(file))) {
                    if (step.uses?.startsWith('actions/setup-java@')) {
                        assert.ok(step.with?.['java-version'], `Missing Java version in ${file}`);
                        assert.ok(step.with?.distribution, `Missing JDK distribution in ${file}`);
                    }
                    if (step.uses?.startsWith('actions/setup-node@')) {
                        assert.equal(step.with?.['node-version'], '22', `Packager requires Node 22 in ${file}`);
                        assert.equal(step.with?.['java-version'], undefined);
                    }
                }
            }
        });

        it('includes the linked learner release documents and offline handoff inputs', async () => {
            const release = await workflow('release.yml');
            const prepare = allSteps(release).find(step => step.name === 'Prepare documentation and examples');
            assert.ok(prepare);
            for (const file of [
                'RELEASE_CHECKLIST.md', 'design/compatibility-profiles.md',
                'design/learner-preview-gates.md', 'design/sap-read-only-integration.md',
                'design/release-support-contract.md', 'tools/learning/evidence.py',
                'tools/learning/test_evidence.py', 'src/main/resources/dhrlang/learn/exercises.json',
                'design/qualification-runbook.md', 'tools/learning/qualification.py',
                'tools/learning/test_qualification.py'
            ]) {
                assert.ok(prepare.run.includes(file), `Missing portable release input: ${file}`);
                await fs.access(path.resolve(__dirname, '..', '..', file));
            }
            assert.ok(prepare.run.includes('git archive HEAD examples/cap-java-mock | tar -x -C build/release'));
            assert.ok(prepare.run.includes('set -o pipefail'), 'CAP archive failures must stop release preparation');
            assert.ok(!prepare.run.includes('cp -R examples/cap-java-mock'),
                'Do not copy CAP credential files or ignored caches into releases');
            assert.ok(allSteps(release).some(step =>
                step.run?.includes("python -m unittest discover -s tools/learning -p 'test_*.py' -v")));
        });

        it('publishes the canonical VSIX without rebuilding and creates its release afterwards', async () => {
            const steps = (await workflow('vscode-extension.yml')).jobs['publish-extension'].steps;
            const verification = steps.findIndex(step => step.run?.includes('npm run verify:package'));
            const publication = steps.findIndex(step => step.run?.includes('vsce publish --packagePath'));
            const release = steps.findIndex(step => step.uses?.startsWith('softprops/action-gh-release'));
            assert.ok(verification >= 0 && publication > verification && release > publication);
            assert.ok(!steps.some(step => step.run?.includes('npm run package')));
            assert.ok(steps.some(step => step.run?.includes('gh release download')));
        });

        it('never guesses a release compiler from a JAR directory listing', async () => {
            for (const file of ['ci.yml', 'release.yml', 'vscode-extension.yml', 'contract-audit.yml']) {
                for (const step of allSteps(await workflow(file))) {
                    assert.doesNotMatch(step.run || '', /(?:ls|find)\s+(?:build\/libs|build-artifacts\/libs)/,
                        `Ambiguous compiler selection in ${file}`);
                }
            }
        });
    });

    for (const missing of [
        'compiler/DhrLang.jar', 'out/extension.js', 'readme.md', 'LICENSE.txt', 'images/icon.png',
        'node_modules/vscode-languageclient/package.json'
    ]) {
        it(`rejects a VSIX missing ${missing}`, async () => {
            const { compiler, zip } = await fixtures();
            zip.remove(`extension/${missing}`);
            await assert.rejects(
                verifyPackage(await zip.generateAsync({ type: 'nodebuffer' }), compiler, metadata),
                /Missing packaged file/);
        });
    }

    it('rejects the original thin-JAR failure', async () => {
        const { compiler, zip } = await fixtures();
        const thin = await JSZip.loadAsync(compiler);
        thin.remove('org/bouncycastle/crypto/Digest.class');
        const bytes = await thin.generateAsync({ type: 'nodebuffer' });
        zip.file('extension/compiler/DhrLang.jar', bytes);
        await assert.rejects(
            verifyPackage(await zip.generateAsync({ type: 'nodebuffer' }), bytes, metadata),
            /bouncycastle/);
    });

    it('rejects a different compiler bundled inside the VSIX', async () => {
        const { compiler, zip } = await fixtures();
        zip.file('extension/compiler/DhrLang.jar', Buffer.from('stale compiler'));
        await assert.rejects(
            verifyPackage(await zip.generateAsync({ type: 'nodebuffer' }), compiler, metadata),
            /differs from the standalone/);
    });

    for (const [property, value] of [['publisher', 'wrong'], ['version', '3.0.0']]) {
        it(`rejects mismatched extension ${property}`, async () => {
            const { compiler, zip } = await fixtures();
            zip.file('extension/package.json', JSON.stringify({ ...metadata, [property]: value }));
            await assert.rejects(
                verifyPackage(await zip.generateAsync({ type: 'nodebuffer' }), compiler, metadata),
                /publisher|version mismatch/);
        });
    }

    it('checks downloaded artifact hashes and source revision before publishing', async () => {
        const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'dhrlang-package-'));
        try {
            const { compiler, zip } = await fixtures();
            const vsix = await zip.generateAsync({ type: 'nodebuffer' });
            const name = 'dhrlang-vscode-4.0.2.vsix';
            await fs.writeFile(path.join(directory, 'DhrLang.jar'), compiler);
            await fs.writeFile(path.join(directory, name), vsix);
            await fs.writeFile(path.join(directory, 'release-manifest.json'), JSON.stringify({
                schemaVersion: 1, version: metadata.version, sourceRevision: 'test-commit',
                sha256: { 'DhrLang.jar': digest(compiler), [name]: digest(vsix) }
            }));
            await verifyRelease(directory, metadata, 'test-commit');
            await assert.rejects(verifyRelease(directory, metadata, 'different-commit'), /same commit/);
            await fs.appendFile(path.join(directory, name), 'tampered');
            await assert.rejects(verifyRelease(directory, metadata, 'test-commit'), /checksum mismatch/);
        } finally {
            await fs.rm(directory, { recursive: true, force: true });
        }
    });
});
