// Place 3 frames at y >= 7600 on the "Moving Walls Platform - Documentation Diagrams" board.
// Each frame: 3000 x 2000, stacked vertically with 400px gap.
//   Frame 1: Multi-Channel Campaign Creation Flow
//   Frame 2: Cinema Targeting & Operator Picker Flow
//   Frame 3: IMS <-> Planner Data Dependency

const tok = process.env.MIRO_API_TOKEN;
const BOARD_ID = 'uXjVGHwGbyo=';
const H = { Authorization: 'Bearer ' + tok, Accept: 'application/json', 'Content-Type': 'application/json' };
const API = `https://api.miro.com/v2/boards/${encodeURIComponent(BOARD_ID)}`;

async function api(path, method='GET', body=null) {
  const r = await fetch(`${API}${path}`, { method, headers: H, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${t.slice(0,300)}`);
  return t ? JSON.parse(t) : {};
}

// Verify max y
async function probeMaxY() {
  let cursor = null, max = 0, count = 0;
  do {
    const url = `/items?limit=50${cursor ? `&cursor=${cursor}` : ''}`;
    const page = await api(url);
    for (const it of page.data || []) {
      const y = (it.position?.y ?? 0);
      const h = (it.geometry?.height ?? 0);
      const bottom = y + h/2;
      if (bottom > max) max = bottom;
      count++;
    }
    cursor = page.cursor || null;
  } while (cursor);
  return { max, count };
}

const { max, count } = await probeMaxY();
console.log(`Existing items: ${count}, max bottom Y: ${Math.round(max)}`);

// Layout
const FRAME_W = 3000, FRAME_H = 2400, GAP = 400;
const Y_START = Math.max(7600, Math.round(max) + 400);
const X_CENTER = 0;

const frames = [
  { title: 'MW Planner — Multi-Channel Campaign Creation Flow', y: Y_START + FRAME_H/2,         color: '#7B1FA2' },
  { title: 'MW Planner — Cinema Targeting & Operator Picker',   y: Y_START + FRAME_H*1.5 + GAP, color: '#0277BD' },
  { title: 'MW Planner — IMS ↔ Planner Data Dependency',        y: Y_START + FRAME_H*2.5 + GAP*2, color: '#2E7D32' },
];

const created = { frames: [], shapes: [], connectors: [] };

async function createFrame(title, y, color) {
  const f = await api('/frames', 'POST', {
    data: { title, format: 'custom', type: 'freeform' },
    position: { x: X_CENTER, y, origin: 'center' },
    geometry: { width: FRAME_W, height: FRAME_H },
    style: { fillColor: '#ffffff' },
  });
  console.log('frame', title, '->', f.id);
  return f;
}

// Place shapes in absolute board coords overlaying each frame (no parent — Miro v2
// parent semantics are inconsistent for shapes; the visual grouping is identical).
async function shape({ frameId, frameY, dx, dy, w, h, text, shape='round_rectangle', fill='#FFE0B2', font=24, color }) {
  return api('/shapes', 'POST', {
    data: { content: `<p><strong>${text}</strong></p>`, shape },
    position: { x: X_CENTER + dx, y: frameY + dy, origin: 'center' },
    geometry: { width: w, height: h },
    style: { fillColor: fill, fontSize: String(font), textAlign: 'center', textAlignVertical: 'middle', borderColor: color || '#424242', borderWidth: '2' },
  });
}

async function text({ frameId, frameY, dx, dy, w, value, font=18, h=80 }) {
  return api('/shapes', 'POST', {
    data: { content: `<p>${value}</p>`, shape: 'rectangle' },
    position: { x: X_CENTER + dx, y: frameY + dy, origin: 'center' },
    geometry: { width: w, height: h },
    style: { fillColor: '#ffffff', fillOpacity: '0', borderColor: '#ffffff', borderWidth: '2', borderOpacity: '0', fontSize: String(font), textAlign: 'center', textAlignVertical: 'middle' },
  });
}

async function connect(startId, endId, label='') {
  return api('/connectors', 'POST', {
    startItem: { id: startId },
    endItem: { id: endId },
    captions: label ? [{ content: label }] : undefined,
    style: { strokeColor: '#424242', strokeWidth: '2', endStrokeCap: 'arrow' },
  });
}

// ============ FRAME 1: Multi-Channel Campaign Creation Flow ============
{
  const F = frames[0];
  const fr = await createFrame(F.title, F.y, F.color);
  created.frames.push(fr);

  // Title bar
  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: -880, w: 2800, h: 80,
    value: '<strong style="font-size:32px;color:#7B1FA2">Multi-channel sequential branching — wizard flow</strong>', font: 32 });
  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: -820, w: 2800, h: 60,
    value: '<em>Step 1 fans out by selected channels; targeting/inventory/schedule run sequentially per channel; Step 4 aggregates.</em>' });

  const steps = [
    { dx: -1200, dy: -500, w: 480, h: 200, text: 'Step 1<br/>Campaign Details<br/><span style="font-size:14px;color:#555">Name · External ID · Plan Dates · Brand · <strong>Media Channels</strong> · Client Type</span>', fill: '#E1BEE7' },
    { dx: -600, dy: -500, w: 480, h: 200, text: 'Step 2<br/>Budget &amp; Location<br/><span style="font-size:14px;color:#555">Budget · Currency · Countries (drives cinema operator filter)</span>', fill: '#C5CAE9' },
    { dx: 0, dy: -500, w: 480, h: 200, text: 'Step 3<br/>Targeting<br/><span style="font-size:14px;color:#555">Demographics · Geofencing · Signals · <strong>Cinema (conditional)</strong></span>', fill: '#B2DFDB' },
    { dx: 600, dy: -500, w: 480, h: 200, text: 'Step 4<br/>Inventories + Plan Summary<br/><span style="font-size:14px;color:#555">Auto-Plan · per-channel rollup</span>', fill: '#C8E6C9' },
    { dx: 1200, dy: -500, w: 480, h: 200, text: 'Step 5<br/>Schedule<br/><span style="font-size:14px;color:#555">Per-inventory 24×7 grid · weekday/time weights</span>', fill: '#FFE0B2' },
  ];
  const stepShapes = [];
  for (const s of steps) stepShapes.push(await shape({ frameId: fr.id, frameY: F.y, ...s, font: 18 }));
  for (let i=0; i<stepShapes.length-1; i++) await connect(stepShapes[i].id, stepShapes[i+1].id);

  // Channel branching swimlane
  await text({ frameId: fr.id, frameY: F.y, dx: -1300, dy: -200, w: 400, h: 60, value: '<strong style="font-size:22px">Selected channels (Step 1)</strong>' });
  const channels = [
    { dx: -1100, dy: -100, label: 'Billboard', fill: '#1976D2' },
    { dx: -700,  dy: -100, label: 'Radio',     fill: '#7B1FA2' },
    { dx: -300,  dy: -100, label: 'Cinema',    fill: '#C2185B' },
    { dx: 100,   dy: -100, label: 'Retail',    fill: '#F57C00' },
    { dx: 500,   dy: -100, label: 'Mobile',    fill: '#0288D1' },
  ];
  const chShapes = [];
  for (const c of channels) {
    chShapes.push(await shape({ frameId: fr.id, frameY: F.y, dx: c.dx, dy: c.dy, w: 320, h: 100, text: `<span style="color:white">${c.label}</span>`, fill: c.fill, color: c.fill, font: 22 }));
  }

  // Per-channel pass loop
  const pass = await shape({ frameId: fr.id, frameY: F.y, dx: 1100, dy: -100, w: 600, h: 280,
    text: 'For each selected channel:<br/>1. Run Step 3 targeting (cinema sub-tab if Cinema)<br/>2. Run Step 4 inventory pick<br/>3. Run Step 5 schedule<br/>4. "Save &amp; next channel"', fill: '#FFF9C4', font: 18 });
  for (const cs of chShapes) await connect(cs.id, pass.id);

  // Aggregation
  const agg = await shape({ frameId: fr.id, frameY: F.y, dx: 0, dy: 250, w: 1600, h: 200,
    text: 'Step 4 Plan Summary aggregates per-channel: counts · geography · est. impressions / reach / frequency · est. cost', fill: '#DCEDC8', font: 20 });
  await connect(pass.id, agg.id);

  // Submit
  const submit = await shape({ frameId: fr.id, frameY: F.y, dx: 0, dy: 550, w: 600, h: 160,
    text: 'Submit → approval workflow<br/><span style="font-size:14px">agency_acceptance → platform_review → media_owner_approval</span>', fill: '#FFCCBC', font: 20 });
  await connect(agg.id, submit.id);

  // Footer
  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: 870, w: 2800, h: 60,
    value: '<em>Today: channel selector + cinema targeting shipped. Sequential per-channel state and per-channel Plan Summary tracked under T003/T006.</em>' });
}

// ============ FRAME 2: Cinema Targeting & Operator Picker Flow ============
{
  const F = frames[1];
  const fr = await createFrame(F.title, F.y, F.color);
  created.frames.push(fr);

  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: -880, w: 2800, h: 80,
    value: '<strong style="font-size:32px;color:#0277BD">Cinema targeting — country-aware operator picker</strong>', font: 32 });
  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: -820, w: 2800, h: 60,
    value: '<em>Cinema sub-tab appears only when Step 1 mediaChannels.includes("cinema"). Operators are filtered by Step 2 countries.</em>' });

  const triggerStep1 = await shape({ frameId: fr.id, frameY: F.y, dx: -1100, dy: -550, w: 600, h: 180,
    text: 'Step 1 — Media Channels<br/><strong>Cinema ✓</strong>', fill: '#E1BEE7', font: 22 });
  const step2Countries = await shape({ frameId: fr.id, frameY: F.y, dx: 200, dy: -550, w: 700, h: 180,
    text: 'Step 2 — Countries<br/><span style="font-size:16px">e.g. India, Malaysia, Singapore</span>', fill: '#C5CAE9', font: 22 });
  const cinemaTab = await shape({ frameId: fr.id, frameY: F.y, dx: 1200, dy: -550, w: 500, h: 180,
    text: 'Step 3 — Cinema sub-tab<br/><span style="font-size:14px">becomes visible</span>', fill: '#B2DFDB', font: 22 });
  await connect(triggerStep1.id, cinemaTab.id, 'enables tab');
  await connect(step2Countries.id, cinemaTab.id, 'filters operators');

  const lookupSrc = await shape({ frameId: fr.id, frameY: F.y, dx: -1100, dy: -200, w: 700, h: 180,
    text: 'shared/cinema-operators.ts<br/><span style="font-size:14px">18 countries · 86 operators</span>', fill: '#FFF9C4', font: 22 });
  const lookupFn = await shape({ frameId: fr.id, frameY: F.y, dx: 200, dy: -200, w: 500, h: 180,
    text: 'getCinemaOperatorsForCountries()', fill: '#FFE0B2', font: 22 });
  await connect(lookupSrc.id, lookupFn.id);
  await connect(step2Countries.id, lookupFn.id);
  await connect(lookupFn.id, cinemaTab.id);

  // Operator examples
  const ops = [
    { dx: -1100, dy: 200, t: 'IN — PVR INOX, Cinépolis IN, Carnival, Miraj' },
    { dx: -300,  dy: 200, t: 'MY — GSC, TGV' },
    { dx: 500,   dy: 200, t: 'SG — Shaw, Golden Village' },
    { dx: 1300,  dy: 200, t: 'AE — VOX, Reel, Roxy, Cinépolis ME' },
  ];
  for (const o of ops) {
    const s = await shape({ frameId: fr.id, frameY: F.y, dx: o.dx, dy: o.dy, w: 700, h: 140, text: o.t, fill: '#E0F2F1', font: 16 });
    await connect(cinemaTab.id, s.id);
  }

  // Cinema fields
  const fields = [
    { dx: -1100, dy: 480, t: '<strong>Operators</strong> (multi-select)<br/><span style="font-size:13px">Filtered by countries</span>' },
    { dx: -350,  dy: 480, t: '<strong>Ad Placement</strong><br/><span style="font-size:13px">Pre-Show / Intermission / Post-Show<br/>(matches IMS field)</span>' },
    { dx: 400,   dy: 480, t: '<strong>Showtime bands</strong><br/><span style="font-size:13px">Weekday/Weekend × Matinee/Prime · Late</span>' },
    { dx: 1150,  dy: 480, t: '<strong>Genres + Ratings</strong><br/><span style="font-size:13px">17 genres · U/PG/PG-13/R/NC-17</span>' },
  ];
  for (const f of fields) await shape({ frameId: fr.id, frameY: F.y, dx: f.dx, dy: f.dy, w: 700, h: 200, text: f.t, fill: '#F8BBD0', font: 16 });

  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: 720, w: 2800, h: 60,
    value: '<em>All cinema fields stored under targeting.cinema. Field names mirror IMS Leisure &gt; Movie Theatres so payloads round-trip without remapping.</em>' });
}

// ============ FRAME 3: IMS <-> Planner Data Dependency ============
{
  const F = frames[2];
  const fr = await createFrame(F.title, F.y, F.color);
  created.frames.push(fr);

  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: -880, w: 2800, h: 80,
    value: '<strong style="font-size:32px;color:#2E7D32">IMS ↔ Planner data dependency</strong>', font: 32 });
  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: -820, w: 2800, h: 60,
    value: '<em>Planner reads from IMS. IMS owns the inventory catalogue, classification taxonomy, showtime feed and DCP/KDM operational metadata.</em>' });

  const ims = await shape({ frameId: fr.id, frameY: F.y, dx: -900, dy: -300, w: 900, h: 600,
    text: '<strong style="font-size:24px;color:white">IMS</strong><br/><br/><span style="color:white;font-size:16px">' +
          '• Inventory catalogue (id, lat/lng, dimensions, format, owner)<br/>' +
          '• Classification taxonomy (Tier 1/2/3) — Leisure &gt; Movie Theatres<br/>' +
          '• Cinema showtime feed<br/>' +
          '• Ad Placement Type (Pre/Intermission/Post)<br/>' +
          '• Availability windows + rate cards<br/>' +
          '• DCP + KDM delivery metadata<br/>' +
          '• TMS workflow integration</span>', fill: '#388E3C', color: '#1B5E20', font: 22 });

  const planner = await shape({ frameId: fr.id, frameY: F.y, dx: 900, dy: -300, w: 900, h: 600,
    text: '<strong style="font-size:24px;color:white">MW Planner</strong><br/><br/><span style="color:white;font-size:16px">' +
          '• Multi-channel campaign wizard<br/>' +
          '• OpenOOH venue selector (mirrors IMS taxonomy)<br/>' +
          '• Cinema operator picker (Planner-side typed array)<br/>' +
          '• Targeting → cinema sub-tree (mirrors IMS field names)<br/>' +
          '• Plan Summary + Auto-Plan engine<br/>' +
          '• Approval workflow + Price Management<br/>' +
          '• Carbon emission rollup</span>', fill: '#1976D2', color: '#0D47A1', font: 22 });

  await connect(ims.id, planner.id, 'reads inventory + showtimes');
  await connect(planner.id, ims.id, 'writes booking requests');

  // Gaps box
  const gaps = await shape({ frameId: fr.id, frameY: F.y, dx: 0, dy: 350, w: 2400, h: 280,
    text: '<strong style="font-size:22px">Known gaps (T001) — none blocking this release</strong><br/><br/><span style="font-size:16px;text-align:left">' +
          '1. Cinema operator entity not first-class in IMS — Planner carries it<br/>' +
          '2. Showtime band concept (Weekend prime, etc.) — Planner-side bucketing<br/>' +
          '3. Ad placement type is cinema-only in IMS — radio/DOOH use different attributes<br/>' +
          '4. Operator premium brands (Director\'s Cut, Aurum) — Planner stores them locally</span>', fill: '#FFF59D', font: 18 });
  await connect(planner.id, gaps.id);
  await connect(ims.id, gaps.id);

  await text({ frameId: fr.id, frameY: F.y, dx: 0, dy: 720, w: 2800, h: 60,
    value: '<em>Full dependency mapping: docs/ims-dependency-map.md  ·  Venue taxonomy: docs/venue-taxonomy.md</em>' });
}

console.log('\nDone.');
console.log(`Created ${created.frames.length} frames at Y range ${frames[0].y - FRAME_H/2} to ${frames[2].y + FRAME_H/2}.`);
console.log('Open: https://miro.com/app/board/' + BOARD_ID);
