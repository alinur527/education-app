import { readdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath, URL } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('../dist/', import.meta.url));
async function collect(dir, prefix = '') {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const name = prefix + entry.name;
    if (entry.isDirectory()) out.push(...(await collect(path.join(dir, entry.name), name + '/')));
    else if (/\.(?:js|css|woff2?|svg|png|webmanifest)$/.test(name) || name === 'index.html')
      out.push('/' + name);
  }
  return out;
}
const assets = (await collect(root)).sort(),
  hash = createHash('sha256');
for (const asset of assets) hash.update(await readFile(path.join(root, asset.slice(1))));
const template = await readFile(new URL('./service-worker.template', import.meta.url), 'utf8');
await writeFile(
  path.join(root, 'sw.js'),
  `const STATIC_CACHE='education-shell-${hash.digest('hex').slice(0, 16)}';\nconst ASSETS=${JSON.stringify(assets)};\n` +
    template,
);
