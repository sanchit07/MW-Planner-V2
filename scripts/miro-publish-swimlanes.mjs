#!/usr/bin/env node
// Reservation Workflow + Price Negotiation swimlanes.
// Item positions are relative to PARENT TOP-LEFT (Miro v2 quirk when parent is set).

const TOKEN = process.env.MIRO_API_TOKEN;
const BOARD = 'uXjVGEva6dA=';
if (!TOKEN) { console.error('MIRO_API_TOKEN missing'); process.exit(1); }

const BASE = `https://api.miro.com/v2/boards/${encodeURIComponent(BOARD)}`;

async function api(path, method = 'GET', body) {
  const r = await fetch(BASE + path, {
    method,
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const t = await r.text();
  if (!r.ok) throw new Error(`${method} ${path} ${r.status}: ${t}`);
  return t.length ? JSON.parse(t) : {};
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function frame(title, x, y, w, h) {
  const f = await api('/frames', 'POST', {
    data: { title, format: 'custom', type: 'freeform' },
    style: { fillColor: '#f1f5f9' },
    position: { x, y }, geometry: { width: w, height: h },
  });
  await sleep(80);
  return f.id;
}

// rx, ry are top-left-relative coords (0..W, 0..H), describing the CENTER of the item.
async function shape(parentId, kind, content, rx, ry, w, h, fill, color = '#1e293b') {
  const r = await api('/shapes', 'POST', {
    data: { shape: kind, content: `<p style="text-align:center;font-weight:600;">${content}</p>` },
    style: { fillColor: fill, color, fontSize: 14, textAlign: 'center', textAlignVertical: 'middle', borderColor: '#1e293b', borderWidth: 2 },
    position: { x: rx, y: ry }, geometry: { width: w, height: h },
    parent: { id: parentId },
  });
  await sleep(70);
  return r.id;
}

async function text(parentId, content, rx, ry, w, color = '#1e293b') {
  const r = await api('/shapes', 'POST', {
    data: { shape: 'rectangle', content: `<p style="text-align:center;font-weight:700;font-size:18px;color:${color};">${content}</p>` },
    style: { fillColor: '#f1f5f9', borderColor: '#cbd5e1', borderWidth: 1.1, color, fontSize: 18, textAlign: 'center', textAlignVertical: 'middle' },
    position: { x: rx, y: ry }, geometry: { width: w, height: 50 },
    parent: { id: parentId },
  });
  await sleep(70);
  return r.id;
}

async function note(parentId, content, rx, ry, color = 'light_yellow') {
  const r = await api('/sticky_notes', 'POST', {
    data: { content, shape: 'square' },
    style: { fillColor: color, textAlign: 'left', textAlignVertical: 'top' },
    position: { x: rx, y: ry }, geometry: { width: 280 },
    parent: { id: parentId },
  });
  await sleep(70);
  return r.id;
}

async function arrow(fromId, toId, label, fromPos = 'right', toPos = 'left', color = '#374151') {
  const pos = (p) => ({
    x: p === 'right' ? '100%' : p === 'left' ? '0%' : '50%',
    y: p === 'top' ? '0%' : p === 'bottom' ? '100%' : '50%',
  });
  const r = await api('/connectors', 'POST', {
    startItem: { id: fromId, position: pos(fromPos) },
    endItem:   { id: toId,   position: pos(toPos) },
    style: { startStrokeCap: 'none', endStrokeCap: 'arrow', strokeColor: color, strokeWidth: 2, fontSize: 12, color },
    captions: label ? [{ content: label, position: '50%' }] : undefined,
    shape: 'curved',
  });
  await sleep(70);
  return r.id;
}

const C = {
  buyer: '#dbeafe', seller: '#ede9fe', system: '#e2e8f0',
  ok: '#d1fae5', bad: '#fee2e2', warn: '#fef3c7', state: '#cffafe',
};

// Frame canvas: W=4000, H=3000. Lane centers at x=700, 2000, 3300.
const xB = 700, xS = 2000, xO = 3300;

// ===================== RESERVATION =====================
async function buildReservation() {
  const FX = -3000, FY = 18000, W = 4000, H = 3000;
  const fid = await frame('PLANNER — Reservation Workflow (Buyer · System · Seller Swimlanes + Downstream Impact)', FX, FY, W, H);

  await text(fid, 'BUYER (Agency)',       xB, 120, 500, '#1e3a8a');
  await text(fid, 'SYSTEM (Planner)',     xS, 120, 500, '#334155');
  await text(fid, 'SELLER (Media Owner)', xO, 120, 500, '#5b21b6');

  // BUYER column
  const b1 = await shape(fid, 'round_rectangle', 'Adds inventory<br/>in wizard Step 4',          xB, 280, 320, 100, C.buyer);
  const b2 = await shape(fid, 'round_rectangle', 'Clicks Submit<br/>(Draft → Planned)',          xB, 440, 320, 100, C.buyer);

  // SYSTEM column
  const s1 = await shape(fid, 'rectangle', 'Writes one HOLD REQUESTED<br/>per inventory',         xS, 440, 360, 100, C.system);
  const s2 = await shape(fid, 'rectangle', 'Opens negotiation thread<br/>@ rate-card per line item', xS, 580, 360, 100, C.system);
  const s3 = await shape(fid, 'rectangle', 'Hides line item<br/>from other tenants (real-time)', xS, 720, 360, 100, C.system);

  // SELLER column
  const o1 = await shape(fid, 'round_rectangle', 'Pending Hold tile<br/>increments on dashboard', xO, 280, 360, 100, C.seller);
  const o2 = await shape(fid, 'round_rectangle', 'Opens Reservations queue<br/>row: campaign · agency · dates · price', xO, 440, 380, 110, C.seller);
  const dec1 = await shape(fid, 'rhombus', 'Seller<br/>decision', xO, 620, 240, 140, C.warn);

  // Branches
  const oA = await shape(fid, 'round_rectangle', 'APPROVE',                          xO - 320, 820, 200, 80, C.ok);
  const oD = await shape(fid, 'round_rectangle', 'DECLINE',                          xO,       820, 200, 80, C.bad);
  const oC = await shape(fid, 'round_rectangle', 'APPROVE w/<br/>conditions',         xO + 320, 820, 220, 90, C.warn);

  // System reactions
  const sysOK   = await shape(fid, 'rectangle', 'Status → RESERVED<br/>Start 7-day expiry timer<br/>Notify buyer', xS, 940,  360, 110, C.system);
  const sysDecl = await shape(fid, 'rectangle', 'Status → DECLINED<br/>Block Stage-3 row<br/>Prompt buyer to swap', xS, 1090, 360, 110, C.system);
  const sysCond = await shape(fid, 'rectangle', 'Open comment thread<br/>Buyer must respond<br/>before honoured',   xS, 1240, 360, 110, C.system);

  // Buyer view of Reserved
  const b3   = await shape(fid, 'round_rectangle', 'Reserved row visible<br/>Extend / Release / Convert<br/>buttons enabled', xB, 940, 340, 110, C.buyer);
  const bExt = await shape(fid, 'round_rectangle', 'Extend',                xB - 220, 1130, 160, 70, C.buyer);
  const bRel = await shape(fid, 'round_rectangle', 'Release',               xB,       1130, 160, 70, C.buyer);
  const bConv= await shape(fid, 'round_rectangle', 'Convert<br/>(if Approved)', xB + 220, 1130, 200, 80, C.buyer);

  // Auto-convert path
  const cAppr   = await shape(fid, 'round_rectangle', 'Campaign reaches<br/>APPROVED (§3.1, §7)', xS, 1480, 360, 100, C.ok);
  const sysBook = await shape(fid, 'rectangle',       'AUTO-CONVERT all<br/>RESERVED → BOOKED<br/>Drop expiry timers', xS, 1640, 360, 110, C.system);

  // Downstream impact
  const impact = await shape(fid, 'rectangle',
    'DOWNSTREAM IMPACT:<br/>· Stage-3 owner approval row advances only after RESERVED<br/>· Stage-3 row blocked while DECLINED — buyer must swap inventory<br/>· Booked reservations unblock Creative Assignment (§11)<br/>· Booked reservations unblock Statement Builder (§12)<br/>· Released / Expired returns inventory to other tenants in real time',
    xS, 1900, 800, 220, C.state, '#0f172a');

  // Expiry sweeper
  const sweep = await shape(fid, 'parallelogram',
    'Nightly sweeper:<br/>RESERVED past 7 days → EXPIRED<br/>(buyer notified, audit row)',
    xO, 1480, 380, 110, C.warn);

  // Edge cases
  await note(fid,
    'EDGE CASES:<br/>• Campaign rejected → all RESERVED auto-Release<br/>• Two buyers same minute → first Submit wins<br/>• Aging Holds badge fires for sellers > 3 days idle<br/>• Extension request is non-blocking — original 7-day clock keeps running',
    xO, 1700, 'light_blue');

  // Arrows
  await arrow(b1, b2, 'plan complete', 'bottom', 'top');
  await arrow(b2, s1, 'submit', 'right', 'left');
  await arrow(s1, s2, '', 'bottom', 'top');
  await arrow(s2, s3, '', 'bottom', 'top');
  await arrow(s1, o1, 'notify owner', 'right', 'left');
  await arrow(o1, o2, '', 'bottom', 'top');
  await arrow(o2, dec1, '', 'bottom', 'top');
  await arrow(dec1, oA, 'approve', 'left', 'top');
  await arrow(dec1, oD, 'decline', 'bottom', 'top');
  await arrow(dec1, oC, 'conditions', 'right', 'top');
  await arrow(oA, sysOK,   '', 'left', 'right');
  await arrow(oD, sysDecl, '', 'left', 'right');
  await arrow(oC, sysCond, '', 'left', 'right');
  await arrow(sysOK, b3, 'broadcast', 'left', 'right');
  await arrow(b3, bExt,  '', 'bottom', 'top');
  await arrow(b3, bRel,  '', 'bottom', 'top');
  await arrow(b3, bConv, 'auto on Approved', 'bottom', 'top');
  await arrow(cAppr, sysBook, 'triggers', 'bottom', 'top');
  await arrow(sysBook, impact, 'unblocks', 'bottom', 'top');
  await arrow(sysOK, sweep, '7-day clock', 'right', 'left', '#92400e');

  console.log('Reservation frame:', fid);
  return fid;
}

// ===================== PRICE NEGOTIATION =====================
async function buildPrice() {
  const FX = 1500, FY = 18000, W = 4000, H = 3000;
  const fid = await frame('PLANNER — Price Negotiation Swimlane (Bilateral-Lock + Ripple to Approval / Proposal / Statement)', FX, FY, W, H);

  await text(fid, 'BUYER (Agency)',       xB, 120, 500, '#1e3a8a');
  await text(fid, 'SYSTEM (Planner)',     xS, 120, 500, '#334155');
  await text(fid, 'SELLER (Media Owner)', xO, 120, 500, '#5b21b6');

  const init  = await shape(fid, 'round_rectangle', 'Line item: RATE CARD<br/>(thread opened on Submit)', xS, 280, 360, 100, C.state);
  const bProp = await shape(fid, 'round_rectangle', 'Buyer clicks<br/>"Propose price X"',                   xB, 440, 320, 100, C.buyer);
  const sysP  = await shape(fid, 'rectangle',       'State → PROPOSED (Buyer)<br/>Day-of-7 timer starts<br/>Amber border on seller side', xS, 440, 380, 110, C.system);

  const sDec  = await shape(fid, 'rhombus', 'Seller within<br/>7 days?', xO, 440, 280, 140, C.warn);

  const sAcc = await shape(fid, 'round_rectangle', 'Accept',                          xO - 350, 660, 180, 80, C.ok);
  const sCnt = await shape(fid, 'round_rectangle', 'Counter Y',                       xO,       660, 180, 80, C.warn);
  const sDcl = await shape(fid, 'round_rectangle', 'Decline',                         xO + 350, 660, 180, 80, C.bad);
  const sExp = await shape(fid, 'round_rectangle', 'No response<br/>Day 8: EXPIRED',  xO + 600, 820, 220, 90, C.bad);

  const sysCnt = await shape(fid, 'rectangle', 'State → COUNTER (Seller)<br/>Timer continues<br/>Amber border on buyer side', xS, 660, 380, 110, C.system);

  const bResp  = await shape(fid, 'rhombus', 'Buyer<br/>response?', xB, 660, 280, 140, C.warn);
  const bAcc   = await shape(fid, 'round_rectangle', 'Accept',                xB - 280, 880, 160, 80, C.ok);
  const bCnt2  = await shape(fid, 'round_rectangle', 'Counter again<br/>(loops back)', xB, 880, 200, 90, C.warn);
  const bDcl   = await shape(fid, 'round_rectangle', 'Decline',               xB + 280, 880, 160, 80, C.bad);

  const sysOne = await shape(fid, 'rectangle',
    'State → ACCEPTED (1 side)<br/>One tick visible<br/>"Awaiting counterparty acceptance" on the other',
    xS, 1000, 420, 130, C.system);

  const dec2 = await shape(fid, 'rhombus', 'Counterparty<br/>also clicks Accept?', xS, 1200, 320, 140, C.warn);

  const lock = await shape(fid, 'round_rectangle',
    '★ PRICE AGREED (LOCKED) ★<br/>Bilateral lock<br/>Row turns light-green · Price field locks',
    xS, 1400, 480, 130, C.ok, '#064e3b');

  const unblock = await shape(fid, 'rectangle',
    'UNBLOCKS:<br/>· Stage-3 approval row for this owner can advance<br/>· Inventory-level "Accepted" badge appears once ALL schedules locked<br/>· Statement Builder can include this line<br/>· Proposal regeneration uses agreed price',
    xS, 1620, 600, 180, C.state, '#0f172a');

  const apprBox = await shape(fid, 'rectangle',
    'IF CAMPAIGN ALREADY APPROVED:<br/>Price change reverts campaign → REVIEWING<br/>Stage 1: stays Approved<br/>Stage 2: stays Approved<br/>Stage 3: ONLY affected owner row → Pending<br/>Other lines stay locked',
    xO, 1400, 600, 200, C.warn, '#7c2d12');

  const propRipple = await shape(fid, 'rectangle',
    'PROPOSAL RIPPLE:<br/>If proposal already SENT:<br/>· Auto-generate new version · Demote old to "superseded"<br/>· Rotate share-link token · Email recipient',
    xO, 1640, 600, 170, C.state, '#0f172a');

  const stmtRipple = await shape(fid, 'rectangle',
    'STATEMENT RIPPLE:<br/>Campaign deferred from current<br/>billing cycle until re-approval done',
    xO, 1840, 600, 100, C.state, '#0f172a');

  await note(fid,
    'BLOCKERS BEFORE NEGOTIATION CAN START:<br/>• Campaign in Rejected/Completed → thread closed<br/>• Inventory "no negotiation" flag → Accept-only at rate-card<br/>• Advertiser role → read-only on all price fields',
    xB, 1400, 'light_pink');

  // Arrows
  await arrow(init, bProp,   '',          'left',   'top');
  await arrow(bProp, sysP,   '',          'right',  'left');
  await arrow(sysP, sDec,    '',          'right',  'left');
  await arrow(sDec, sAcc,    'accept',    'left',   'top');
  await arrow(sDec, sCnt,    'counter',   'bottom', 'top');
  await arrow(sDec, sDcl,    'decline',   'right',  'top');
  await arrow(sDec, sExp,    '8th day',   'right',  'top');
  await arrow(sAcc, sysOne,  'one tick',  'left',   'right');
  await arrow(sCnt, sysCnt,  '',          'left',   'right');
  await arrow(sysCnt, bResp, '',          'left',   'right');
  await arrow(bResp, bAcc,   'accept',    'left',   'top');
  await arrow(bResp, bCnt2,  'counter',   'bottom', 'top');
  await arrow(bResp, bDcl,   'decline',   'right',  'top');
  await arrow(bCnt2, bProp,  'loop',      'top',    'bottom', '#9333ea');
  await arrow(bAcc, sysOne,  'one tick',  'right',  'left');
  await arrow(sysOne, dec2,  '',          'bottom', 'top');
  await arrow(dec2, lock,    'YES → bilateral lock', 'bottom', 'top', '#059669');
  await arrow(lock, unblock, '',          'bottom', 'top');
  await arrow(lock, apprBox, 'if Approved','right', 'left',  '#b45309');
  await arrow(apprBox, propRipple, '',    'bottom', 'top');
  await arrow(propRipple, stmtRipple, '', 'bottom', 'top');
  await arrow(sExp, init,    'reverts',   'top',    'right', '#dc2626');
  await arrow(sDcl, init,    'reverts',   'top',    'right', '#dc2626');
  await arrow(bDcl, init,    'reverts',   'top',    'left',  '#dc2626');

  console.log('Price frame:', fid);
  return fid;
}

const targets = process.argv.slice(2);
const wantR = targets.length === 0 || targets.includes('reservation');
const wantP = targets.length === 0 || targets.includes('price');

let r, p;
if (wantR) r = await buildReservation();
if (wantP) p = await buildPrice();
console.log('\nDone.');
if (r) console.log('Reservation:', `https://miro.com/app/board/${BOARD}/?moveToWidget=${r}`);
if (p) console.log('Price:',       `https://miro.com/app/board/${BOARD}/?moveToWidget=${p}`);
