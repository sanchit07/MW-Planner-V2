// PRD v13 §7 — Two-Tier Approval + Execution Handoff diagram.
// Adds ONE frame on board uXjVGEva6dA= at y >= 21000.
// Pattern adapted from scripts/miro-publish-v2.mjs: items live on the board
// with absolute coordinates (origin:'center'), NOT parented to the frame.
// Idempotent: removes any existing frame with the same title (and orphaned
// frames left by failed earlier runs) before rebuilding.

const tok = process.env.MIRO_API_TOKEN;
if (!tok) { console.error('Missing MIRO_API_TOKEN'); process.exit(1); }
const BOARD_ID = 'uXjVGEva6dA=';
const H = { Authorization: 'Bearer ' + tok, Accept: 'application/json', 'Content-Type': 'application/json' };
const API = `https://api.miro.com/v2/boards/${encodeURIComponent(BOARD_ID)}`;
const FRAME_TITLE = 'Two-Tier Approval + Execution Handoff (PRD §7 v13)';

async function api(path, method = 'GET', body = null) {
  const r = await fetch(`${API}${path}`, { method, headers: H, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${t.slice(0, 400)}`);
  if (r.status === 204 || !t) return null;
  return JSON.parse(t);
}

async function probeBoard() {
  let cursor = null, maxBottom = 0, count = 0;
  const sameTitleFrames = [];
  const emptyFramesAbove20k = [];
  do {
    const url = `/items?limit=50${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
    const page = await api(url);
    for (const it of page.data || []) {
      count++;
      const y = it.position?.y ?? 0;
      const h = it.geometry?.height ?? 0;
      const bottom = y + h / 2;
      if (it.type === 'frame') {
        if (it.data?.title === FRAME_TITLE) sameTitleFrames.push(it.id);
        else if (y > 20000) emptyFramesAbove20k.push({ id: it.id, title: it.data?.title, y });
      } else if (bottom > maxBottom) maxBottom = bottom;
    }
    cursor = page.cursor || null;
  } while (cursor);
  return { maxBottom, count, sameTitleFrames, emptyFramesAbove20k };
}

const probe = await probeBoard();
console.log(`Existing items: ${probe.count}, max non-frame bottom Y: ${Math.round(probe.maxBottom)}`);
for (const id of probe.sameTitleFrames) {
  console.log(`Removing existing frame "${FRAME_TITLE}" (${id})`);
  await api(`/frames/${id}`, 'DELETE').catch(e => console.log('  delete err:', e.message));
}
for (const f of probe.emptyFramesAbove20k) {
  // Frames left empty by failed earlier runs (titled with our diagram name variants)
  if (!f.title || f.title === FRAME_TITLE) {
    console.log(`Removing orphan frame at y=${f.y} (${f.id})`);
    await api(`/frames/${f.id}`, 'DELETE').catch(e => console.log('  delete err:', e.message));
  }
}

// ----- Layout (absolute board coords) ---------------------------------------
const FRAME_W = 3600, FRAME_H = 2400;
const Y_CENTER = Math.max(21000 + FRAME_H / 2, Math.round(probe.maxBottom) + 600 + FRAME_H / 2);
const X_CENTER = 0;
const LEFT = X_CENTER - FRAME_W / 2;
const TOP = Y_CENTER - FRAME_H / 2;

console.log(`Placing frame center at (${X_CENTER}, ${Y_CENTER}) — bounds y:[${TOP}, ${TOP + FRAME_H}]`);

// Create frame
const frame = await api('/frames', 'POST', {
  data: { title: FRAME_TITLE, type: 'freeform', format: 'custom' },
  style: { fillColor: '#f9fafb' },
  position: { x: X_CENTER, y: Y_CENTER, origin: 'center' },
  geometry: { width: FRAME_W, height: FRAME_H },
});
console.log('Frame created:', frame.id);

async function shape(kind, text, x, y, w, h, fill, textColor = '#111827', borderColor = null, sub = '') {
  const html = `<p><strong>${text}</strong></p>` + (sub ? `<p style="font-size:11px;">${sub}</p>` : '');
  const r = await api('/shapes', 'POST', {
    data: { shape: kind, content: html },
    style: {
      fillColor: fill, color: textColor,
      borderColor: borderColor || fill, borderWidth: 2,
      fontSize: 14, textAlign: 'center', textAlignVertical: 'middle',
    },
    position: { x, y, origin: 'center' },
    geometry: { width: w, height: h },
  });
  return r.id;
}

async function txt(text, x, y, w, fontSize = 22) {
  const r = await api('/texts', 'POST', {
    data: { content: `<p><strong>${text}</strong></p>` },
    style: { fontSize, color: '#111827', textAlign: 'center' },
    position: { x, y, origin: 'center' },
    geometry: { width: w },
  });
  return r.id;
}

async function note(text, x, y, w = 320, fillColor = 'light_yellow') {
  const r = await api('/sticky_notes', 'POST', {
    data: { content: text, shape: 'square' },
    style: { fillColor, textAlign: 'left' },
    position: { x, y, origin: 'center' },
    geometry: { width: w },
  });
  return r.id;
}

async function arrow(fromId, toId, label = '', startPos = 'right', endPos = 'left') {
  return await api('/connectors', 'POST', {
    startItem: { id: fromId, position: { x: startPos === 'right' ? '100%' : startPos === 'left' ? '0%' : '50%', y: startPos === 'top' ? '0%' : startPos === 'bottom' ? '100%' : '50%' } },
    endItem: { id: toId, position: { x: endPos === 'right' ? '100%' : endPos === 'left' ? '0%' : '50%', y: endPos === 'top' ? '0%' : endPos === 'bottom' ? '100%' : '50%' } },
    style: { startStrokeCap: 'none', endStrokeCap: 'arrow', strokeColor: '#374151', strokeWidth: 2, fontSize: 12, color: '#374151' },
    captions: label ? [{ content: label, position: '50%' }] : undefined,
    shape: 'curved',
  });
}

// Column X positions inside the frame (absolute)
const C1 = LEFT + 400;   // Draft
const C2 = LEFT + 950;   // Submit / Tier 1
const C3 = LEFT + 1500;  // Reviewing
const C4 = LEFT + 2100;  // Tier 2 / fanout
const C5 = LEFT + 2750;  // Activate / RFD column
const C6 = LEFT + 3200;  // Far right

// Row Y positions
const RTITLE = TOP + 90;
const R1 = TOP + 300;   // Draft → Submit → Reviewing
const R2 = TOP + 700;   // Tier 1 / RFD button
const R3 = TOP + 1050;  // Tier 2 / per-MO
const R4 = TOP + 1400;  // Approved
const R5 = TOP + 1750;  // Handoff destinations
const R6 = TOP + 2100;  // Stickies

// Title
await txt('Submit Plan → 2-Tier Approval → Execution Handoff', X_CENTER, RTITLE, 2800, 24);

// Row 1: Draft → Submit Plan → Reviewing
const draft = await shape('round_rectangle', 'Draft', C1, R1, 220, 90, '#e5e7eb', '#111827', '#9ca3af');
const submit = await shape('round_rectangle', 'Submit Plan (creator only)', C3, R1, 320, 90, '#10b981', '#ffffff', '#047857');
const reviewing = await shape('round_rectangle', 'Status: Reviewing', C5, R1, 280, 90, '#fef3c7', '#111827', '#d97706');
await arrow(draft, submit, 'click');
await arrow(submit, reviewing, 'flips status');

// Row 2: Tier 1
const tier1 = await shape('round_rectangle',
  'Tier 1 — Internal Company Approval',
  C2, R2, 480, 130, '#dbeafe', '#1e3a8a', '#1d4ed8',
  'Anyone in creator company with canApproveCampaigns. Self-approve OK.');
await arrow(reviewing, tier1, 'opens');

// RFD button branch on the right
const rfdBtn = await shape('round_rectangle',
  'Request for Deal (button)',
  C5, R2, 320, 130, '#ede9fe', '#3b0764', '#6d28d9',
  'Gates: creator-owned · hasActivateAccess · NOT media_owner');
await arrow(reviewing, rfdBtn, 'optional', 'bottom', 'top');

// Comment-based programmatic switch (alternative path)
const commentSwitch = await shape('round_rectangle',
  'Comment to MO (alt programmatic path)',
  C6, R3, 360, 100, '#e0e7ff', '#1e1b4b', '#4338ca',
  'No DSP needed · MO flips line type at their end');
await arrow(reviewing, commentSwitch, 'alternative', 'bottom', 'top');

// Row 3: Tier 2 + per-MO fanout
const tier2 = await shape('round_rectangle',
  'Tier 2 — Media Owner Approval (per-MO)',
  C2, R3, 480, 130, '#fce7f3', '#831843', '#be185d',
  'All MOs must approve. Decline → Partial → swap inv. in Price Mgmt.');
await arrow(tier1, tier2, 'on approve', 'bottom', 'top');

const moA = await shape('round_rectangle', 'MO A', C2 - 260, R3 + 220, 130, 70, '#ffffff', '#111827', '#9ca3af');
const moB = await shape('round_rectangle', 'MO B', C2, R3 + 220, 130, 70, '#ffffff', '#111827', '#9ca3af');
const moC = await shape('round_rectangle', 'MO C', C2 + 260, R3 + 220, 130, 70, '#ffffff', '#111827', '#9ca3af');
await arrow(tier2, moA, '', 'bottom', 'top');
await arrow(tier2, moB, '', 'bottom', 'top');
await arrow(tier2, moC, '', 'bottom', 'top');

// Row 4: Approved
const approved = await shape('round_rectangle', 'Approved', C2, R4 + 50, 280, 100, '#10b981', '#ffffff', '#047857');
await arrow(moA, approved, '', 'bottom', 'top');
await arrow(moB, approved, '', 'bottom', 'top');
await arrow(moC, approved, '', 'bottom', 'top');

// Row 5: Handoff destinations
const influence = await shape('round_rectangle',
  'Influence — Direct/Standard',
  C1, R5, 320, 110, '#d1fae5', '#064e3b', '#047857',
  'one line item per MO · digital inv.');
const oms = await shape('round_rectangle',
  'OMS — Direct/Standard',
  C2, R5, 320, 110, '#fef9c3', '#713f12', '#a16207',
  'one line item per MO · classic inv.');
const both = await shape('round_rectangle',
  'Both (mixed plan)',
  C3 + 180, R5, 280, 110, '#fed7aa', '#7c2d12', '#c2410c',
  'split at line-item level');
const activate = await shape('round_rectangle',
  'Activate — Programmatic Deal',
  C5, R5, 360, 110, '#ede9fe', '#3b0764', '#6d28d9',
  'one deal per MO · seeded for DSP');

await arrow(approved, influence, 'all digital', 'bottom', 'top');
await arrow(approved, oms, 'all classic', 'bottom', 'top');
await arrow(approved, both, 'mixed', 'bottom', 'top');
await arrow(approved, activate, 'rfdRequested', 'right', 'top');
await arrow(rfdBtn, activate, 'flips destination', 'bottom', 'top');
await arrow(commentSwitch, oms, 'MO flips', 'left', 'top');
await arrow(commentSwitch, influence, 'MO flips', 'left', 'top');

// Row 6: Stickies / footnotes
await note(
  '🔒 Tier 1 server gate: actor.primaryCompanyId === creator.primaryCompanyId AND actor.canApproveCampaigns === true',
  LEFT + 450, R6 + 50, 320, 'light_yellow'
);
await note(
  '🔒 RFD server gate: caller owns campaign AND hasActivateAccess AND NOT media_owner',
  LEFT + 1250, R6 + 50, 320, 'light_pink'
);
await note(
  '🛡️ MO-led plan: when creator company owns ALL inventory, Tier 2 auto-skips via checkSelfApprovalScenario',
  LEFT + 2050, R6 + 50, 320, 'light_green'
);
await note(
  '📜 execution_handoffs table = append-only audit (campaignId · destination · trigger · payload · createdAt)',
  LEFT + 2850, R6 + 50, 320, 'light_blue'
);

console.log('OK — frame "%s" built.', FRAME_TITLE);
console.log('Open: https://miro.com/app/board/' + BOARD_ID + '/');
