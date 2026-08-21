// Publish flowchart-style diagrams to the correct Miro board (Reserve Campaign Feature board)
// Uses proper flowchart shapes:
//   round_rectangle = start/end (terminator)
//   rectangle       = process step
//   rhombus         = decision (diamond)
//   parallelogram   = input/output
//   circle          = off-page connector
// Colour conventions:
//   #2563eb (blue)   = Agency / Buyer action
//   #f59e0b (amber)  = Media Owner / Seller action
//   #7c3aed (purple) = Internal / Platform action
//   #6b7280 (gray)   = System / automatic
//   #16a34a (green)  = Terminal "approved/active" state
//   #dc2626 (red)    = Terminal "rejected/expired" state

const TOKEN = process.env.MIRO_API_TOKEN;
const BOARD = 'uXjVGEva6dA=';
const API = `https://api.miro.com/v2/boards/${encodeURIComponent(BOARD)}`;

if (!TOKEN) { console.error('MIRO_API_TOKEN missing'); process.exit(1); }

async function api(path, method='GET', body=null) {
  const opts = { method, headers: { Authorization: 'Bearer ' + TOKEN, Accept: 'application/json' } };
  if (body) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
  const r = await fetch(API + path, opts);
  if (!r.ok) {
    const txt = await r.text();
    throw new Error(`${method} ${path} -> ${r.status}: ${txt.slice(0,300)}`);
  }
  if (r.status === 204) return null;
  return await r.json();
}

async function frame(title, x, y, w, h) {
  const r = await api('/frames', 'POST', {
    data: { title, type: 'freeform', format: 'custom' },
    style: { fillColor: '#ffffffff' },
    position: { x, y, origin: 'center' },
    geometry: { width: w, height: h }
  });
  console.log('  frame:', title, '->', r.id);
  return r.id;
}

async function shape(kind, text, x, y, w, h, fill, textColor='#ffffff', borderColor=null) {
  const r = await api('/shapes', 'POST', {
    data: { shape: kind, content: `<p><strong>${text}</strong></p>` },
    style: {
      fillColor: fill,
      color: textColor,
      borderColor: borderColor || fill,
      borderWidth: 2,
      fontSize: 14,
      textAlign: 'center',
      textAlignVertical: 'middle'
    },
    position: { x, y, origin: 'center' },
    geometry: { width: w, height: h }
  });
  return r.id;
}

async function note(text, x, y, w, fill='#fef3c7') {
  const r = await api('/sticky_notes', 'POST', {
    data: { content: text, shape: 'square' },
    style: { fillColor: 'light_yellow', textAlign: 'center' },
    position: { x, y, origin: 'center' },
    geometry: { width: w }
  });
  return r.id;
}

async function txt(text, x, y, w, fontSize=22, color='#111827', bold=true) {
  const inner = bold ? `<strong>${text}</strong>` : text;
  const r = await api('/texts', 'POST', {
    data: { content: `<p>${inner}</p>` },
    style: { fontSize, color, textAlign: 'center' },
    position: { x, y, origin: 'center' },
    geometry: { width: w }
  });
  return r.id;
}

async function arrow(fromId, toId, label='', startPos='right', endPos='left') {
  return await api('/connectors', 'POST', {
    startItem: { id: fromId, position: { x: startPos==='right'?'100%':startPos==='left'?'0%':'50%', y: startPos==='top'?'0%':startPos==='bottom'?'100%':'50%' } },
    endItem:   { id: toId,   position: { x: endPos==='right'?'100%':endPos==='left'?'0%':'50%',   y: endPos==='top'?'0%':endPos==='bottom'?'100%':'50%' } },
    style: { startStrokeCap: 'none', endStrokeCap: 'arrow', strokeColor: '#374151', strokeWidth: 2, fontSize: 12, color: '#374151' },
    captions: label ? [{ content: label, position: '50%' }] : undefined,
    shape: 'curved'
  });
}

const C = {
  agency: '#2563eb', owner: '#f59e0b', internal: '#7c3aed', system: '#6b7280',
  ok: '#16a34a', bad: '#dc2626', neutral: '#475569'
};

// =============================================================================
// FRAME 1: CAMPAIGN STATUS LIFECYCLE (state machine + locks)
// =============================================================================
async function buildCampaignLifecycle() {
  const FX = 0, FY = 6500, FW = 3200, FH = 2000;
  const id = await frame('PLANNER — Campaign Status Lifecycle & Locks', FX, FY, FW, FH);

  await txt('Campaign Status Lifecycle', FX, FY - FH/2 + 60, 2200, 28);
  await txt('States, transitions, who triggers them, and what is locked', FX, FY - FH/2 + 100, 2400, 14, '#6b7280', false);

  // States arranged horizontally
  const yMain = FY - 100;
  const xs = [FX-1400, FX-840, FX-280, FX+280, FX+840, FX+1400];
  const draft     = await shape('round_rectangle', 'DRAFT',     xs[0], yMain, 200, 100, C.system);
  const planned   = await shape('round_rectangle', 'PLANNED',   xs[1], yMain, 200, 100, C.agency);
  const reviewing = await shape('round_rectangle', 'REVIEWING', xs[2], yMain, 200, 100, C.internal);
  const approved  = await shape('round_rectangle', 'APPROVED',  xs[3], yMain, 200, 100, C.ok);
  const active    = await shape('round_rectangle', 'ACTIVE',    xs[4], yMain, 200, 100, C.ok);
  const completed = await shape('round_rectangle', 'COMPLETED', xs[5], yMain, 200, 100, C.system);

  // Transitions
  await arrow(draft, planned, 'Submit wizard');
  await arrow(planned, reviewing, 'Auto');
  await arrow(reviewing, approved, 'All approvers say yes');
  await arrow(approved, active, 'Start date reached');
  await arrow(active, completed, 'End date reached');

  // Side branches: rejected, paused
  const rejected = await shape('round_rectangle', 'REJECTED', xs[2], yMain + 320, 200, 90, C.bad);
  await arrow(reviewing, rejected, 'Any approver declines', 'bottom', 'top');
  const paused = await shape('round_rectangle', 'PAUSED', xs[4], yMain + 320, 200, 90, C.neutral);
  await arrow(active, paused, 'Manual pause', 'bottom', 'top');
  await arrow(paused, active, 'Resume', 'top', 'bottom');

  // Re-approval loop
  const reapprovalDecision = await shape('rhombus', 'Edit on APPROVED?\nprice / inventory /\nschedule / targeting',
    FX+560, yMain - 320, 280, 180, '#fbbf24', '#000000');
  await arrow(approved, reapprovalDecision, '', 'top', 'bottom');
  await arrow(reapprovalDecision, reviewing, 'Yes — revert', 'left', 'top');

  // Lock conditions box
  await txt('What is LOCKED at each state', FX-1400, yMain + 480, 700, 16, '#111827', true);
  await note('DRAFT — nothing locked. Anyone in tenant can edit.\n\nPLANNED — submitted; cannot edit until reviewing returns.\n\nREVIEWING — pricing & inventory editable by Internal / Media Owner only.\n\nAPPROVED — every action button disabled; banner shown. Any edit reverts to REVIEWING.\n\nACTIVE — only schedule pacing & creative swap allowed. Hard fields immutable.\n\nCOMPLETED / REJECTED — read-only forever.',
    FX-1400, yMain + 700, 1400);

  // Trigger conditions box
  await txt('What TRIGGERS each transition', FX+250, yMain + 480, 700, 16, '#111827', true);
  await note('DRAFT → PLANNED: planner clicks Submit on Step 5.\n\nPLANNED → REVIEWING: automatic on submit.\n\nREVIEWING → APPROVED: ALL three stages pass — agency_acceptance + platform_review + every media_owner.\n\nREVIEWING → REJECTED: any approver declines (single decline ends the review).\n\nAPPROVED → REVIEWING: any change to price, inventory list, schedule or targeting — even by the same user — re-runs all media-owner approvals.',
    FX+250, yMain + 700, 1400);

  // Legend
  await shape('rectangle', 'Agency action',  FX+1100, yMain - 530, 140, 30, C.agency);
  await shape('rectangle', 'Media Owner',    FX+1100, yMain - 490, 140, 30, C.owner);
  await shape('rectangle', 'Internal',       FX+1100, yMain - 450, 140, 30, C.internal);
  await shape('rectangle', 'System / auto',  FX+1100, yMain - 410, 140, 30, C.system);
  await shape('rectangle', 'Approved / OK',  FX+1100, yMain - 370, 140, 30, C.ok);
  await shape('rectangle', 'Rejected / End', FX+1100, yMain - 330, 140, 30, C.bad);

  return id;
}

// =============================================================================
// FRAME 2: NEW CAMPAIGN WIZARD (multi-channel + cinema branch)
// =============================================================================
async function buildWizard() {
  const FX = 0, FY = 8800, FW = 3200, FH = 2000;
  const id = await frame('PLANNER — New Campaign Wizard (Multi-Channel + Cinema Branch)', FX, FY, FW, FH);

  await txt('New Campaign Wizard — Step Flow', FX, FY - FH/2 + 60, 2400, 28);
  await txt('Five steps with cross-step dependencies. Cinema sub-tab only renders when Cinema is among the chosen channels.', FX, FY - FH/2 + 100, 2800, 14, '#6b7280', false);

  // START
  const start = await shape('round_rectangle', 'START\nPlanner clicks New Campaign', FX-1450, FY-400, 240, 110, C.system);

  // STEP 1
  const step1 = await shape('rectangle', 'STEP 1 — Campaign Details\nName · External ID · Plan Dates\nBrand · Media Channels · Client/Agency',
    FX-1100, FY-400, 320, 130, C.agency);
  await arrow(start, step1);

  // Decision: channels chosen?
  const decChannels = await shape('rhombus', 'Cinema in\nMedia Channels?', FX-700, FY-400, 220, 160, '#fbbf24', '#000000');
  await arrow(step1, decChannels);

  // STEP 2
  const step2 = await shape('rectangle', 'STEP 2 — Budget & Location\nTotal budget · Currency · Goal\nTarget countries',
    FX-300, FY-400, 320, 130, C.agency);
  await arrow(decChannels, step2, 'either branch joins', 'right', 'left');

  // STEP 3 — Targeting (with cinema branch)
  const step3 = await shape('rectangle', 'STEP 3 — Targeting\nDemographics · Geofencing\nVenue tree · Audience signals',
    FX+150, FY-500, 320, 130, C.agency);
  await arrow(step2, step3);

  const cinema = await shape('rectangle', 'Cinema sub-tab\nOperators (filtered by Step 2 countries)\nAd Placement · Showtime bands\nGenres · Ratings',
    FX+150, FY-200, 360, 150, '#10b981');
  await arrow(decChannels, cinema, 'Cinema = yes', 'bottom', 'left');
  await arrow(step3, cinema, 'enables', 'bottom', 'top');

  // STEP 4 — Inventories
  const step4 = await shape('rectangle', 'STEP 4 — Inventories\nManual · CSV upload · Auto Plan',
    FX+650, FY-400, 320, 110, C.agency);
  await arrow(step3, step4);
  await arrow(cinema, step4, '', 'right', 'bottom');

  // Decision: Auto Plan?
  const decAuto = await shape('rhombus', 'Use Auto Plan?', FX+1050, FY-400, 200, 140, '#fbbf24', '#000000');
  await arrow(step4, decAuto);

  const autoPlan = await shape('parallelogram', 'Auto Plan Engine\nscores & allocates',
    FX+1400, FY-550, 280, 100, C.internal);
  await arrow(decAuto, autoPlan, 'Yes', 'top', 'left');

  // STEP 5
  const step5 = await shape('rectangle', 'STEP 5 — Schedule\n7×24 grid per inventory\nPresets · Real-time forecast',
    FX+1400, FY-300, 320, 130, C.agency);
  await arrow(decAuto, step5, 'No (manual)', 'bottom', 'left');
  await arrow(autoPlan, step5, '', 'bottom', 'top');

  // Submit + status flip
  const submit = await shape('rhombus', 'All required\nfields valid?', FX+1400, FY+50, 200, 140, '#fbbf24', '#000000');
  await arrow(step5, submit, '', 'bottom', 'top');

  const planned = await shape('round_rectangle', 'Campaign → PLANNED\nApproval workflow starts',
    FX+1100, FY+300, 320, 100, C.ok);
  await arrow(submit, planned, 'Yes', 'left', 'top');

  const stay = await shape('round_rectangle', 'Stay in DRAFT\nshow validation errors',
    FX+1700, FY+300, 320, 100, C.bad);
  await arrow(submit, stay, 'No', 'right', 'top');

  // Footnote on autosave
  await note('Autosave runs in the background on every keystroke (browser) and every 30s (server). A closed tab never loses work.',
    FX-1100, FY+550, 1100);

  return id;
}

// =============================================================================
// FRAME 3: APPROVAL WORKFLOW (three-stage with re-approval loop)
// =============================================================================
async function buildApproval() {
  const FX = 0, FY = 11100, FW = 3200, FH = 2000;
  const id = await frame('PLANNER — Approval Workflow (Three-Stage + Re-approval)', FX, FY, FW, FH);

  await txt('Approval Workflow — Three Stages', FX, FY - FH/2 + 60, 2400, 28);
  await txt('Sequential stages. Every media owner with inventory in the plan must individually approve.', FX, FY - FH/2 + 100, 2800, 14, '#6b7280', false);

  const start = await shape('round_rectangle', 'Campaign\nsubmitted → PLANNED', FX-1400, FY-200, 260, 100, C.system);

  // Stage 1
  const s1 = await shape('rectangle', 'STAGE 1\nAgency Acceptance', FX-1000, FY-200, 260, 100, C.agency);
  await arrow(start, s1);

  const s1d = await shape('rhombus', 'Submitted by\nAgency role?', FX-650, FY-200, 220, 150, '#fbbf24', '#000000');
  await arrow(s1, s1d);

  const s1auto = await shape('parallelogram', 'Auto-pass\n(self-submission)', FX-300, FY-380, 240, 90, C.system);
  await arrow(s1d, s1auto, 'Yes', 'top', 'left');

  // Stage 2
  const s2 = await shape('rectangle', 'STAGE 2\nPlatform Review', FX-300, FY-200, 240, 100, C.internal);
  await arrow(s1d, s2, 'No', 'right', 'left');
  await arrow(s1auto, s2, '', 'bottom', 'top');

  const s2d = await shape('rhombus', 'Internal\napproves?', FX+50, FY-200, 200, 140, '#fbbf24', '#000000');
  await arrow(s2, s2d);

  const reject = await shape('round_rectangle', 'Campaign → REJECTED', FX+50, FY+150, 240, 90, C.bad);
  await arrow(s2d, reject, 'No', 'bottom', 'top');

  // Stage 3 — parallel media owners
  const s3 = await shape('rectangle', 'STAGE 3\nMedia Owner Approval\n(one row per owner)', FX+450, FY-200, 280, 130, C.owner);
  await arrow(s2d, s3, 'Yes', 'right', 'left');

  const mo1 = await shape('rectangle', 'Owner A approves', FX+800, FY-340, 220, 70, C.owner);
  const mo2 = await shape('rectangle', 'Owner B approves', FX+800, FY-260, 220, 70, C.owner);
  const mo3 = await shape('rectangle', 'Owner C approves', FX+800, FY-180, 220, 70, C.owner);
  const mo4 = await shape('rectangle', 'Owner D declines', FX+800, FY-100, 220, 70, C.bad);
  await arrow(s3, mo1, '', 'right', 'left');
  await arrow(s3, mo2, '', 'right', 'left');
  await arrow(s3, mo3, '', 'right', 'left');
  await arrow(s3, mo4, '', 'right', 'left');

  const allDec = await shape('rhombus', 'ALL owners\napproved?', FX+1150, FY-200, 220, 150, '#fbbf24', '#000000');
  await arrow(mo1, allDec, '', 'right', 'top');
  await arrow(mo2, allDec, '', 'right', 'left');
  await arrow(mo3, allDec, '', 'right', 'left');
  await arrow(mo4, allDec, '', 'right', 'bottom');

  const approved = await shape('round_rectangle', 'Campaign → APPROVED\n(LOCKED)', FX+1500, FY-300, 260, 100, C.ok);
  await arrow(allDec, approved, 'Yes', 'top', 'left');

  const swap = await shape('round_rectangle', 'Planner swaps\ndeclined inventory\n(loops to Stage 3)', FX+1500, FY-100, 260, 110, C.neutral);
  await arrow(allDec, swap, 'Any decline', 'bottom', 'left');
  await arrow(swap, s3, 're-fire only\naffected stage', 'left', 'right');

  // Re-approval trigger
  const edit = await shape('rhombus', 'Edit on APPROVED?\n(price / inv / sched\n/ targeting)', FX+1500, FY+250, 280, 180, '#fbbf24', '#000000');
  await arrow(approved, edit, '', 'bottom', 'top');
  await arrow(edit, s3, 'Yes — reset all owner\napprovals to pending', 'left', 'bottom');

  // Security rules note
  await note('SECURITY GUARDS\n\n• Self-approval guard: a user cannot advance the stage they themselves submitted.\n\n• Scope guard: a user can only approve on behalf of media owners whose inventory is actually in the plan. Scope mismatch returns a permission error.\n\n• Tenant guard: switching tenant in another tab does not retro-grant approval rights.',
    FX-1100, FY+450, 1500);

  return id;
}

// =============================================================================
// FRAME 4: PRICE NEGOTIATION STATE MACHINE
// =============================================================================
async function buildPricing() {
  const FX = 0, FY = 13400, FW = 3200, FH = 1800;
  const id = await frame('PLANNER — Price Negotiation State Machine', FX, FY, FW, FH);

  await txt('Price Negotiation — Per Line Item', FX, FY - FH/2 + 60, 2400, 28);
  await txt('Bilateral lock: a line is "Price Agreed" only when BOTH agency and media owner accept.', FX, FY - FH/2 + 100, 2800, 14, '#6b7280', false);

  const rate = await shape('round_rectangle', 'RATE CARD\n(seller default)', FX-1300, FY-100, 240, 100, C.system);

  const propose = await shape('rectangle', 'Buyer proposes\ndiscount / bonus / SOV', FX-950, FY-100, 260, 100, C.agency);
  await arrow(rate, propose);

  const proposed = await shape('round_rectangle', 'PROPOSED\nday 1 of 7', FX-600, FY-100, 200, 90, C.agency);
  await arrow(propose, proposed);

  const ownerDec = await shape('rhombus', 'Media Owner\nresponse', FX-300, FY-100, 220, 160, '#fbbf24', '#000000');
  await arrow(proposed, ownerDec);

  const counter = await shape('round_rectangle', 'COUNTER\nseller offer', FX+50, FY-300, 200, 90, C.owner);
  await arrow(ownerDec, counter, 'Counter', 'top', 'left');
  await arrow(counter, propose, 'Buyer responds', 'left', 'top');

  const accepted = await shape('round_rectangle', 'ACCEPTED\nby Media Owner', FX+50, FY-100, 200, 90, C.ok);
  await arrow(ownerDec, accepted, 'Accept');

  const declined = await shape('round_rectangle', 'DECLINED', FX+50, FY+100, 200, 90, C.bad);
  await arrow(ownerDec, declined, 'Decline', 'bottom', 'left');

  // Bilateral check
  const bilat = await shape('rhombus', 'Buyer also\naccepted?', FX+350, FY-100, 220, 150, '#fbbf24', '#000000');
  await arrow(accepted, bilat);

  const locked = await shape('round_rectangle', 'PRICE AGREED\n(LOCKED — green row)', FX+700, FY-200, 280, 100, C.ok);
  await arrow(bilat, locked, 'Yes', 'right', 'left');

  const wait = await shape('round_rectangle', 'Waiting for buyer\nacceptance', FX+700, FY+50, 280, 90, C.neutral);
  await arrow(bilat, wait, 'No', 'right', 'left');
  await arrow(wait, propose, 'Buyer clicks Accept on table', 'bottom', 'bottom');

  // Expiry
  const expiry = await shape('rhombus', 'Day > 7?', FX-600, FY+250, 200, 130, '#fbbf24', '#000000');
  await arrow(proposed, expiry, '', 'bottom', 'top');
  const expired = await shape('round_rectangle', 'EXPIRED\n(reverts to RATE CARD)', FX-200, FY+250, 280, 100, C.bad);
  await arrow(expiry, expired, 'Yes', 'right', 'left');

  // Re-approval if approved
  const reappr = await shape('rhombus', 'Campaign already\nAPPROVED?', FX+1100, FY-200, 240, 150, '#fbbf24', '#000000');
  await arrow(locked, reappr);
  const revert = await shape('round_rectangle', 'Campaign → REVIEWING\nre-fire approval workflow', FX+1450, FY-200, 320, 110, C.bad);
  await arrow(reappr, revert, 'Yes', 'right', 'left');
  const stay = await shape('round_rectangle', 'Stays as planned', FX+1450, FY-50, 240, 90, C.system);
  await arrow(reappr, stay, 'No', 'bottom', 'left');

  // Three-tier note
  await note('THREE PRICING TIERS\n\nCampaign-tier: one discount across every line ("10% off")\nInventory-tier: discount on every schedule of one inventory ("Free bonus week on Mumbai Airport")\nSchedule-tier: discount on a single time-slot schedule ("Lower CPM Friday late-night only")\n\nThe action bar appears the moment ≥1 row is selected and offers: Accept Price · Apply Discount · Apply Bonus · Change SOV.',
    FX-1100, FY+450, 1700);

  return id;
}

// =============================================================================
// FRAME 5: IMS ↔ PLANNER DATA FLOW
// =============================================================================
async function buildIms() {
  const FX = 0, FY = 15700, FW = 3200, FH = 1800;
  const id = await frame('PLANNER — IMS ↔ Planner Data Dependency', FX, FY, FW, FH);

  await txt('IMS ↔ Planner — Reads, Writes, and Local Workarounds', FX, FY - FH/2 + 60, 2400, 28);
  await txt('Planner is the buyer-facing surface. IMS is the seller-side system of record. Each side owns specific data.', FX, FY - FH/2 + 100, 2800, 14, '#6b7280', false);

  // Two big swimlanes
  const ims = await shape('rectangle', 'IMS\n(Inventory Management System)\nSource of truth for inventory & operations', FX-1100, FY-500, 600, 130, C.owner);
  const pln = await shape('rectangle', 'PLANNER\nBuyer-facing planning workspace', FX+1100, FY-500, 600, 130, C.agency);

  // Reads (IMS → Planner)
  const reads = [
    { label: 'Inventory catalogue', y: -300, freq: 'nightly + on-demand' },
    { label: 'OpenOOH classification taxonomy', y: -220, freq: 'on standard bumps' },
    { label: 'Cinema showtime feed', y: -140, freq: 'hourly' },
    { label: 'Ad placement type (Pre/Inter/Post)', y: -60, freq: 'static' },
    { label: 'Availability windows + rate cards', y:  20, freq: 'nightly + on-demand' },
    { label: 'Creative delivery metadata (DCP/KDM)', y: 100, freq: 'per creative' }
  ];
  for (const r of reads) {
    const rs = await shape('parallelogram', `${r.label}  (${r.freq})`, FX, FY + r.y, 700, 50, C.system);
    await arrow(ims, rs, '', 'right', 'left');
    await arrow(rs, pln, '', 'right', 'left');
  }

  // Writes (Planner → IMS)
  const writes = [
    { label: 'Booking request (on full approval)', y: 220 },
    { label: 'Hold request (on Step 4 add or Reservation)', y: 290 },
    { label: 'Hold release (on release or expiry)', y: 360 },
    { label: 'Creative delivery package (on assignment)', y: 430 }
  ];
  for (const w of writes) {
    const ws = await shape('parallelogram', w.label, FX, FY + w.y, 700, 50, C.agency);
    await arrow(pln, ws, '', 'left', 'right');
    await arrow(ws, ims, '', 'left', 'right');
  }

  // Local workarounds box
  await note('GAPS IN IMS — PLANNER CARRIES THE WORKAROUND\n\n• Cinema operator is not first-class in IMS → Planner ships a curated list (18 countries, 86 operators).\n• Showtime bands ("Weekday Prime" etc.) are not bucketed in IMS → Planner buckets client-side.\n• Ad placement type is cinema-only in IMS → unified contract on the IMS roadmap.\n• Operator premium brands (Director\'s Cut, Aurum, Onyx) are not surfaced as inventory tags in IMS → Planner stores them locally.\n\nEach workaround is retired the moment IMS surfaces the field natively.',
    FX-1300, FY+600, 2600);

  return id;
}

// =============================================================================
// MAIN
// =============================================================================
(async () => {
  console.log('Publishing flowchart diagrams to', BOARD);
  const ids = {};
  ids.lifecycle = await buildCampaignLifecycle();
  ids.wizard    = await buildWizard();
  ids.approval  = await buildApproval();
  ids.pricing   = await buildPricing();
  ids.ims       = await buildIms();
  console.log('\nDeep links:');
  for (const [k,v] of Object.entries(ids)) {
    console.log(`  ${k}: https://miro.com/app/board/${BOARD}/?moveToWidget=${v}`);
  }
  console.log('\nDone.');
})().catch(e => { console.error(e); process.exit(1); });
