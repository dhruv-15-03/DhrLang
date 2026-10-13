const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { copyFile, mkdir, mkdtemp, readFile, readdir, realpath, rm, stat, writeFile } =
  require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');
const { buildModel } = require('./build-model.cjs');

async function fixture(t, source) {
  const root = await mkdtemp(path.join(os.tmpdir(), 'dhrlang-cap-model-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  await mkdir(path.join(root, 'srv'));
  if (source === undefined) {
    await copyFile(
      path.resolve(__dirname, '..', 'srv', 'purchase-service.cds'),
      path.join(root, 'srv', 'purchase-service.cds'),
    );
  } else {
    await writeFile(path.join(root, 'srv', 'purchase-service.cds'), source, 'utf8');
  }
  return root;
}

function digest(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

test('the pinned APIs preserve the original Java model and OData metadata bytes', async t => {
  const root = await fixture(t);
  const files = await buildModel(root);
  const canonical = await realpath(root);
  assert.deepEqual(files.map(file => path.relative(canonical, file)), [
    path.join('srv', 'src', 'main', 'resources', 'edmx', 'csn.json'),
    path.join('srv', 'src', 'main', 'resources', 'edmx', 'odata', 'v4', 'PurchaseLab.xml'),
  ]);
  const csn = await readFile(files[0]);
  const xml = await readFile(files[1]);
  assert.equal(digest(csn), 'ad6fe8b8b476a8e9e26410a9187a5e3bef5cf2159fe948d80a56c75698126136');
  assert.equal(digest(xml), 'e1dca514c08abf4f9647a5fee3c4cdd8760ae1288f0ee0e0cd4e6bb37806587c');
  const definitions = JSON.parse(csn).definitions;
  assert.equal(definitions.PurchaseLab['@requires'], 'authenticated-user');
  assert.equal(definitions['PurchaseLab.approve']['@requires'], 'Approver');
  assert.equal(definitions['PurchaseLab.ApprovalCommand'].elements.requestVersion.type, 'cds.String');
  assert.equal(definitions['PurchaseLab.ApprovalCommand'].elements.requestVersion['@mandatory'], true);
});

test('malformed CDS fails without retaining stale generated artifacts', async t => {
  const root = await fixture(t, 'service PurchaseLab { this is not valid CDS; }');
  const resources = path.join(root, 'srv', 'src', 'main', 'resources');
  const output = path.join(resources, 'edmx');
  await mkdir(output, { recursive: true });
  await writeFile(path.join(output, 'csn.json'), '{"stale":true}', 'utf8');
  await writeFile(path.join(resources, 'keep.txt'), 'unrelated resource', 'utf8');
  await assert.rejects(buildModel(root), /CDS compilation failed/);
  await assert.rejects(stat(output), { code: 'ENOENT' });
  assert.equal(await readFile(path.join(resources, 'keep.txt'), 'utf8'), 'unrelated resource');
});

test('a model without a service fails rather than generating success-shaped output', async t => {
  const root = await fixture(t, 'type Example : String;');
  await assert.rejects(buildModel(root), /service/i);
  await assert.rejects(stat(path.join(root, 'srv', 'src', 'main', 'resources', 'edmx')), {
    code: 'ENOENT',
  });
});

test('rebuilding removes only obsolete generated metadata', async t => {
  const root = await fixture(t, 'service PurchaseLab {} service OldService {}');
  await buildModel(root);
  const output = path.join(root, 'srv', 'src', 'main', 'resources', 'edmx');
  assert.deepEqual((await readdir(path.join(output, 'odata', 'v4'))).sort(), [
    'OldService.xml', 'PurchaseLab.xml',
  ]);
  await writeFile(path.join(root, 'srv', 'purchase-service.cds'), 'service PurchaseLab {}', 'utf8');
  await buildModel(root);
  assert.deepEqual(await readdir(path.join(output, 'odata', 'v4')), ['PurchaseLab.xml']);
});
