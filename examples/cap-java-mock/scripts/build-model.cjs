const cds = require('@sap/cds');
const path = require('node:path');
const { mkdir, realpath, rm, writeFile } = require('node:fs/promises');

async function buildModel(root) {
  root = await realpath(root);
  const source = path.join(root, 'srv');
  const output = path.join(source, 'src', 'main', 'resources', 'edmx');
  await rm(output, { recursive: true, force: true });

  const outbox = cds.resolve('@sap/cds/srv/outbox');
  if (!outbox) throw new Error('Cannot resolve the pinned CAP outbox model');
  const loaded = await cds.load([path.join(source, 'purchase-service.cds'), ...outbox], {
    cwd: root,
    docs: true,
  });
  const model = cds.compile.for.java(loaded, { version: 'v4', docs: true });
  const json = cds.compile.to.json(model, { cwd: root, src: source });
  const metadata = [...cds.compile.to.edmx(model, {
    service: 'all',
    version: 'v4',
    docs: true,
  })];
  if (metadata.length === 0) {
    throw new Error('The local CAP model must contain an OData service');
  }
  for (const [, info] of metadata) {
    if (typeof info.file !== 'string' || !info.file ||
        info.file === '.' || info.file === '..' || /[\\/:\0]/.test(info.file)) {
      throw new Error('The local CAP model has an invalid metadata filename');
    }
  }

  const odata = path.join(output, 'odata', 'v4');
  await mkdir(odata, { recursive: true });
  const files = [path.join(output, 'csn.json')];
  await writeFile(files[0], json, 'utf8');
  for (const [xml, info] of metadata) {
    const file = path.join(odata, `${info.file}.xml`);
    await writeFile(file, xml, 'utf8');
    files.push(file);
  }
  return files;
}

if (require.main === module) {
  const root = path.resolve(__dirname, '..');
  buildModel(root).then(files => {
    for (const file of files) console.log(path.relative(root, file));
  }).catch(error => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = { buildModel };
