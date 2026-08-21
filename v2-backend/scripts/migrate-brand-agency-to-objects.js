/**
 * Migration: convert flat brandId/brandName/agencyId fields to nested objects.
 *
 * Before:
 *   { brandId: "...", brandName: "...", agencyId: "..." }
 *
 * After:
 *   { brand: { id: "...", name: "..." }, agency: { id: "..." } }
 *
 * Run with:
 *   mongosh <connection-string> mw-planner --file scripts/migrate-brand-agency-to-objects.js
 *
 * Dry-run (no writes):
 *   mongosh <connection-string> mw-planner --eval "var DRY_RUN=true" --file scripts/migrate-brand-agency-to-objects.js
 */

const isDryRun = typeof DRY_RUN !== "undefined" && DRY_RUN === true;

if (isDryRun) {
  print("=== DRY RUN — no documents will be modified ===\n");
}

const candidates = db.campaigns.find({
  $or: [{ brandId: { $exists: true } }, { agencyId: { $exists: true } }],
});

let total = 0;
let updated = 0;
let errors = 0;

candidates.forEach((campaign) => {
  total++;

  const set = {};
  const unset = {};

  if (campaign.brandId) {
    set.brand = { id: campaign.brandId, name: campaign.brandName || null };
    unset.brandId = "";
    unset.brandName = "";
  }

  if (campaign.agencyId) {
    set.agency = { id: campaign.agencyId };
    unset.agencyId = "";
  }

  if (Object.keys(set).length === 0) {
    return;
  }

  const summary = [
    set.brand ? `brand={id:${set.brand.id}, name:${set.brand.name}}` : "",
    set.agency ? `agency={id:${set.agency.id}}` : "",
  ]
    .filter(Boolean)
    .join("  ");

  print(`  ${isDryRun ? "WOULD UPDATE" : "UPDATING"} _id=${campaign._id}  ${summary}`);

  if (!isDryRun) {
    try {
      db.campaigns.updateOne({ _id: campaign._id }, { $set: set, $unset: unset });
      updated++;
    } catch (e) {
      print(`  ERROR on _id=${campaign._id}: ${e.message}`);
      errors++;
    }
  } else {
    updated++;
  }
});

print("");
print(
  `Done. Candidates: ${total} | ${isDryRun ? "Would update" : "Updated"}: ${updated} | Errors: ${errors}`
);
