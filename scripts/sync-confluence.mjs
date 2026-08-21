#!/usr/bin/env node
import fs from 'node:fs/promises';

const PAGE_ID = process.env.CONFLUENCE_PAGE_ID || '66060301';
const BASE = process.env.CONFLUENCE_BASE_URL || 'https://movingwallshub.atlassian.net/wiki';
const SOURCE = process.env.CONFLUENCE_PAYLOAD_PATH || 'docs/confluence-payload-mw-planner-prd.xml';

const email = process.env.CONFLUENCE_EMAIL;
const token = process.env.CONFLUENCE_TOKEN;
if (!email || !token) {
  console.error('CONFLUENCE_EMAIL and CONFLUENCE_TOKEN must be set in env');
  process.exit(1);
}
const auth = 'Basic ' + Buffer.from(`${email}:${token}`).toString('base64');

const raw = await fs.readFile(SOURCE, 'utf8');
const body = raw.replace(/^[\s\S]*?-->\s*/, '').trim();

const g = await fetch(`${BASE}/api/v2/pages/${PAGE_ID}?body-format=storage`, {
  headers: { Authorization: auth, Accept: 'application/json' },
});
if (!g.ok) {
  console.error('GET failed', g.status, await g.text());
  process.exit(1);
}
const cur = await g.json();
console.log(`Current: "${cur.title}" v${cur.version?.number} (space ${cur.spaceId})`);

const next = (cur.version?.number || 0) + 1;
const message = process.argv.slice(2).join(' ') || 'Sync from docs/PRD_MW_PLANNER.md';
const payload = {
  id: PAGE_ID,
  status: 'current',
  title: cur.title,
  body: { representation: 'storage', value: body },
  version: { number: next, message },
};
const p = await fetch(`${BASE}/api/v2/pages/${PAGE_ID}`, {
  method: 'PUT',
  headers: { Authorization: auth, 'Content-Type': 'application/json', Accept: 'application/json' },
  body: JSON.stringify(payload),
});
const pt = await p.text();
if (!p.ok) {
  console.error('PUT failed', p.status, pt.slice(0, 800));
  process.exit(1);
}
const result = JSON.parse(pt);
console.log(`Updated to v${result.version?.number} — ${BASE}/spaces/${cur.spaceId}/pages/${PAGE_ID}`);
