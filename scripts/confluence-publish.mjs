import fs from 'node:fs/promises';

const md = await fs.readFile('docs/PRD_MW_PLANNER.md', 'utf-8');

// Confluence "full-width" layout for the body uses ~1280px; 1240
// leaves a small visual breathing margin. Tables emit data-layout
// "full-width" so they span the same width as paragraphs.
const TARGET_TABLE_WIDTH = 1240;
const ABSOLUTE_MIN_COL_WIDTH = 60;
const MAX_COL_WIDTH = 480;
// Confluence table body text renders at ~7.6 px/char on average plus
// ~24 px of cell padding/gutter. Keep this slightly generous so single
// long words ("Reviewing", "Approved", "Completed", "Performance") are
// never broken across two lines.
const PX_PER_CHAR = 7.6;
const CELL_PADDING_PX = 24;

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function inline(s) {
  s = esc(s);
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/(^|[\s(])\*([^*\n]+)\*(?=$|[\s).,;:!?])/g, '$1<em>$2</em>');
  s = s.replace(/(^|[^!])\[([^\]]+)\]\(([^)]+)\)/g, '$1<a href="$3">$2</a>');
  return s;
}

function plainLength(cellMd) {
  let s = cellMd
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*\n]+)\*/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
  const words = s.split(/\s+/).filter(Boolean);
  const longestWord = words.reduce((m, w) => Math.max(m, w.length), 0);
  return Math.max(s.length, longestWord);
}

function longestWordLen(cellMd) {
  const s = cellMd
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*\n]+)\*/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
  // Hyphenated tokens can break at the hyphen, so each segment counts
  // separately. Slashes and en/em-dashes also break naturally.
  const tokens = s.split(/[\s/\-–—]+/).filter(Boolean);
  return tokens.reduce((m, w) => Math.max(m, w.length), 0);
}

function computeColWidths(rows) {
  if (rows.length === 0 || rows[0].length === 0) return [];
  const colCount = rows[0].length;
  const sums = new Array(colCount).fill(0);
  const maxes = new Array(colCount).fill(0);
  // Per-column hard minimum: enough pixels so the longest single word
  // (anywhere in the column, including the header) renders on one line.
  const wordMins = new Array(colCount).fill(0);
  for (const row of rows) {
    for (let c = 0; c < colCount; c++) {
      const len = plainLength(row[c] || '');
      sums[c] += len;
      if (len > maxes[c]) maxes[c] = len;
      const wl = longestWordLen(row[c] || '');
      const wpx = Math.ceil(wl * PX_PER_CHAR + CELL_PADDING_PX);
      if (wpx > wordMins[c]) wordMins[c] = wpx;
    }
  }
  const minWidths = wordMins.map((w) => Math.max(ABSOLUTE_MIN_COL_WIDTH, w));
  const weights = sums.map((sum, c) => {
    const avg = sum / rows.length;
    const peak = maxes[c];
    return Math.max(8, 0.6 * avg + 0.4 * Math.min(peak, 80));
  });
  const totalWeight = weights.reduce((a, b) => a + b, 0);
  let widths = weights.map(w => Math.round((w / totalWeight) * TARGET_TABLE_WIDTH));
  // Apply per-column minimums (so long single words never wrap) and
  // global maximum.
  for (let i = 0; i < widths.length; i++) {
    if (widths[i] < minWidths[i]) widths[i] = minWidths[i];
    if (widths[i] > MAX_COL_WIDTH) widths[i] = MAX_COL_WIDTH;
  }
  // Rebalance to hit TARGET_TABLE_WIDTH while honouring per-column
  // minimums. When shrinking, never drop a column below its word-mininum
  // (we'd rather overflow the target than break a single word).
  let diff = TARGET_TABLE_WIDTH - widths.reduce((a, b) => a + b, 0);
  let safety = colCount * 8;
  while (diff !== 0 && safety-- > 0) {
    const order = widths
      .map((w, i) => ({ w, i }))
      .sort((a, b) => diff > 0 ? b.w - a.w : a.w - b.w);
    let moved = false;
    for (const { i } of order) {
      if (diff > 0 && widths[i] < MAX_COL_WIDTH) { widths[i]++; diff--; moved = true; }
      else if (diff < 0 && widths[i] > minWidths[i]) { widths[i]--; diff++; moved = true; }
      if (diff === 0) break;
    }
    if (!moved) break;
  }
  return widths;
}

function emitTable(headerCells, bodyRows) {
  const allRows = [headerCells, ...bodyRows];
  const widths = computeColWidths(allRows);
  const colgroup = '<colgroup>' + widths.map(w => `<col style="width: ${w}px;"/>`).join('') + '</colgroup>';
  const thead = '<thead><tr>' + headerCells.map(c => `<th><p>${inline(c)}</p></th>`).join('') + '</tr></thead>';
  const tbody = '<tbody>' + bodyRows.map(row =>
    '<tr>' + row.map(c => `<td><p>${inline(c)}</p></td>`).join('') + '</tr>'
  ).join('') + '</tbody>';
  // data-layout="full-width" makes the table span the same width as the
  // body paragraphs (matches the page's full-width content layout).
  const totalWidth = widths.reduce((a, b) => a + b, 0);
  return `<table data-layout="full-width" data-table-width="${totalWidth}">${colgroup}${thead}${tbody}</table>`;
}

function mdToStorage(src) {
  const lines = src.split('\n');
  const out = [];
  let inCode = false, codeLang = '', codeBuf = [];
  let tableHeader = null, tableRows = [];
  let listType = null;
  let para = [];
  const flushPara = () => { if (para.length) { out.push('<p>' + inline(para.join(' ')) + '</p>'); para = []; } };
  const flushList = () => { if (listType) { out.push('</' + listType + '>'); listType = null; } };
  const flushTable = () => {
    if (tableHeader) {
      out.push(emitTable(tableHeader, tableRows));
      tableHeader = null; tableRows = [];
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = (lines[i] || '').replace(/\s+$/, '');
    if (inCode) {
      if (line.startsWith('```')) {
        out.push('<ac:structured-macro ac:name="code"><ac:parameter ac:name="language">' + esc(codeLang || 'text') + '</ac:parameter><ac:plain-text-body><![CDATA[' + codeBuf.join('\n') + ']]></ac:plain-text-body></ac:structured-macro>');
        inCode = false; codeBuf = []; codeLang = '';
      } else codeBuf.push(line);
      continue;
    }
    if (line.startsWith('```')) {
      flushPara(); flushList(); flushTable();
      inCode = true; codeLang = line.slice(3).trim();
      continue;
    }
    if (/^\|.*\|$/.test(line)) {
      const cells = line.slice(1, -1).split('|').map(c => c.trim());
      const next = (lines[i + 1] || '').trim();
      if (!tableHeader && /^\|[\s|:-]+\|$/.test(next)) {
        flushPara(); flushList();
        tableHeader = cells; tableRows = [];
        i++;
        continue;
      }
      if (tableHeader) {
        tableRows.push(cells);
        continue;
      }
    } else if (tableHeader) {
      flushTable();
    }

    if (/^---+$/.test(line)) {
      flushPara(); flushList(); flushTable();
      out.push('<hr/>');
      continue;
    }
    let m;
    if ((m = line.match(/^(#{1,6})\s+(.*)$/))) {
      flushPara(); flushList(); flushTable();
      const lvl = m[1].length;
      out.push(`<h${lvl}>${inline(m[2])}</h${lvl}>`);
      continue;
    }
    if ((m = line.match(/^[-*]\s+(.*)$/))) {
      flushPara(); flushTable();
      if (listType !== 'ul') { flushList(); out.push('<ul>'); listType = 'ul'; }
      out.push('<li>' + inline(m[1]) + '</li>');
      continue;
    }
    if ((m = line.match(/^\d+\.\s+(.*)$/))) {
      flushPara(); flushTable();
      if (listType !== 'ol') { flushList(); out.push('<ol>'); listType = 'ol'; }
      out.push('<li>' + inline(m[1]) + '</li>');
      continue;
    }
    if (line === '') { flushPara(); flushList(); flushTable(); continue; }
    para.push(line);
  }
  flushPara(); flushList(); flushTable();
  return out.join('\n');
}

const storage = mdToStorage(md);
console.log('Storage chars:', storage.length);

const email = process.env.CONFLUENCE_EMAIL;
const token = process.env.CONFLUENCE_API_TOKEN || process.env.CONFLUENCE_TOKEN;
if (!email || !token) { console.error('Missing CONFLUENCE_EMAIL / CONFLUENCE_API_TOKEN'); process.exit(1); }
const auth = 'Basic ' + Buffer.from(`${email}:${token}`).toString('base64');
const PAGE_ID = '66060301';
const base = process.env.CONFLUENCE_BASE_URL || 'https://movingwallshub.atlassian.net/wiki';

const probe = await fetch(`${base}/api/v2/pages/${PAGE_ID}`, { headers: { Authorization: auth, Accept: 'application/json' } });
console.log('probe', base, '->', probe.status);
if (!probe.ok) { console.error(await probe.text()); process.exit(2); }
const page = await probe.json();
console.log('Current title:', page.title, 'version:', page.version.number, 'spaceId:', page.spaceId);

const body = {
  id: PAGE_ID,
  status: page.status || 'current',
  title: 'MW Planner — Product Requirements Document',
  spaceId: page.spaceId,
  body: { representation: 'storage', value: storage },
  version: {
    number: page.version.number + 1,
    message: 'Replace Auto Plan with Recommendation Engine §6, rewrite §4.4 Inventories with manual edit drawer, update all cross-references',
  },
};
const upd = await fetch(`${base}/api/v2/pages/${PAGE_ID}`, {
  method: 'PUT',
  headers: { Authorization: auth, Accept: 'application/json', 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});
const updTxt = await upd.text();
console.log('PUT status:', upd.status);
console.log('Response (first 400):', updTxt.slice(0, 400));
if (!upd.ok) process.exit(3);

async function setAppearance(key, value) {
  const url = `${base}/rest/api/content/${PAGE_ID}/property/${key}`;
  const cur = await fetch(url, { headers: { Authorization: auth, Accept: 'application/json' } });
  if (cur.status === 200) {
    const j = await cur.json();
    const r = await fetch(url, {
      method: 'PUT',
      headers: { Authorization: auth, Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value, version: { number: j.version.number + 1 } }),
    });
    console.log(`appearance ${key} updated:`, r.status);
  } else {
    const r = await fetch(url, {
      method: 'POST',
      headers: { Authorization: auth, Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value }),
    });
    console.log(`appearance ${key} created:`, r.status);
  }
}

await setAppearance('content-appearance-published', 'full-width');
await setAppearance('content-appearance-draft', 'full-width');

console.log('OK — page updated. Open:', `${base}/spaces/${page.spaceId}/pages/${PAGE_ID}`);
