const https = require('https');
const fs = require('fs');
const path = require('path');

const BASE = 'https://cdn.jsdelivr.net/npm/cn-fontsource-source-han-serif-sc-vf/';
const DEST = path.join(__dirname, '..', 'public', 'fonts');

function download(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, (res) => {
      if (res.statusCode !== 200) {
        reject(new Error(`HTTP ${res.statusCode} for ${url}`));
        return;
      }
      res.pipe(file);
      file.on('finish', () => { file.close(); resolve(); });
    }).on('error', (err) => { fs.unlinkSync(dest); reject(err); });
  });
}

async function main() {
  if (fs.existsSync(DEST)) {
    const existing = fs.readdirSync(DEST).filter(f => f.endsWith('.woff2'));
    if (existing.length > 100) {
      console.log(`Found ${existing.length} woff2 files already in ${DEST}, skipping download`);
      return;
    }
  }
  fs.mkdirSync(DEST, { recursive: true });

  // Download font.css
  console.log('Downloading font.css...');
  const cssPath = path.join(DEST, 'font.css');
  await download(BASE + 'font.css', cssPath);
  console.log('font.css downloaded');

  // Parse woff2 URLs
  const css = fs.readFileSync(cssPath, 'utf-8');
  const regex = /url\('([^']+\.woff2)'\)/g;
  const files = [];
  let m;
  while ((m = regex.exec(css)) !== null) {
    files.push(m[1]);
  }
  console.log(`Found ${files.length} woff2 files to download`);

  // Download in batches of 10
  const BATCH = 10;
  for (let i = 0; i < files.length; i += BATCH) {
    const batch = files.slice(i, i + BATCH);
    await Promise.all(batch.map(f => {
      const dest = path.join(DEST, f);
      if (fs.existsSync(dest)) {
        return Promise.resolve();
      }
      console.log(`Downloading ${f}...`);
      return download(BASE + f, dest);
    }));
    console.log(`Batch ${Math.floor(i / BATCH) + 1}/${Math.ceil(files.length / BATCH)} complete`);
  }

  console.log('All fonts downloaded!');
  const total = fs.readdirSync(DEST).filter(f => f.endsWith('.woff2')).length;
  console.log(`Total woff2 files: ${total}`);
}

main().catch(console.error);
