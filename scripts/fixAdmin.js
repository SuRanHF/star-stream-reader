const fs = require('fs');
let c = fs.readFileSync('public/admin.html', 'utf8');

// Fix 1: revokeTitle line
// Build the correct line using careful JS double-quote escaping
const correctLine = "          pt.map(function(t) { return t.name + ' <button class=\"admin-btn danger\" onclick=\"Admin.revokeTitle(\\'' + t.title_key + '\\')\" style=\"font-size:9px;padding:0 4px;\">移除</button>'; }).join(' | ') +";

// Find and replace the broken line
const lines = c.split('\n');
for (let i = 0; i < lines.length; i++) {
  if (lines[i].includes('pt.map') && lines[i].includes('revokeTitle')) {
    console.log('Replacing line', i+1);
    console.log('  OLD:', lines[i].substring(0, 80));
    lines[i] = correctLine;
    console.log('  NEW:', lines[i].substring(0, 80));
    break;
  }
}

c = lines.join('\n');

// Fix 2: restore edit-row structure for checkbox section
// Find the broken block:
//   '</div>' +          (closes title row)
//     '<label ...>' +   (orphaned, no <div class="edit-row"> wrapper)
// And fix it by restoring the wrapper
const brokenBlock = "'</div>' +\n          '<label style=\"font-size:12px;color:#6a6a78;margin-right:16px;\">' +";
const fixedBlock = "'</div>' +\n        '<div class=\"edit-row\">' +\n          '<span class=\"edit-label\"></span>' +\n          '<label style=\"font-size:12px;color:#6a6a78;margin-right:16px;\">' +";

if (c.includes(brokenBlock)) {
  c = c.replace(brokenBlock, fixedBlock);
  console.log('Fixed edit-row structure');
} else {
  console.log('edit-row structure not found, checking alternatives...');
  // Check what's actually there
  const idx = c.indexOf("持有称号");
  if (idx > 0) {
    console.log('Context after 持有称号:', JSON.stringify(c.substring(idx, idx + 500)));
  }
}

fs.writeFileSync('public/admin.html', c);
console.log('Done');
