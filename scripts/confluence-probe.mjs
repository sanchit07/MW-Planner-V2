const email = process.env.CONFLUENCE_EMAIL;
const token = process.env.CONFLUENCE_TOKEN;
const auth = 'Basic ' + Buffer.from(`${email}:${token}`).toString('base64');
const PAGE_ID = '66060301';

const subs = [
  'movingwalls','moving-walls','mw','mwalls','movingwallsgroup','movingwallsasia',
  'mw-team','mwgroup','movingwalls-group','planner','mw-planner'
];
const candidates = [];
for (const s of subs) {
  candidates.push(`https://${s}.atlassian.net/wiki`);            // Cloud
  candidates.push(`https://${s}.atlassian.net`);                  // Cloud no /wiki
  candidates.push(`https://confluence.${s}.com`);                 // Server
  candidates.push(`https://wiki.${s}.com`);                       // Server
}

async function tryProbe(b) {
  // v2 cloud
  try {
    const r = await fetch(`${b}/api/v2/pages/${PAGE_ID}`, { headers: { Authorization: auth, Accept: 'application/json' } });
    if (r.ok) return { base: b, mode: 'v2', body: await r.json() };
    if (r.status === 401) return { base: b, mode: 'v2-401' };
  } catch {}
  // v1 server / cloud rest
  try {
    const r = await fetch(`${b}/rest/api/content/${PAGE_ID}?expand=version,space,body.storage`, { headers: { Authorization: auth, Accept: 'application/json' } });
    if (r.ok) return { base: b, mode: 'v1', body: await r.json() };
    if (r.status === 401) return { base: b, mode: 'v1-401' };
  } catch {}
  return null;
}

const results = await Promise.allSettled(candidates.map(tryProbe));
for (let i=0; i<results.length; i++) {
  const v = results[i].value;
  if (v) console.log('HIT:', candidates[i], v.mode, v.body?.title || '');
}
console.log('---probe complete---');
