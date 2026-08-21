import { describe, expect, it } from "vitest";

import { formatAuditMessage } from "../auditMessage.utils";

describe("formatAuditMessage", () => {
  it("converts raw map syntax to readable pairs without braces", () => {
    expect(formatAuditMessage("Budget Allocation:{digital=100.0}")).toBe(
      "Budget Allocation: Digital: 100.0",
    );
  });

  it("handles multi-entry maps", () => {
    expect(
      formatAuditMessage("Budget Allocation:{digital=60.0, classic=40.0}"),
    ).toBe("Budget Allocation: Digital: 60.0, Classic: 40.0");
  });

  it("converts SCREAMING_SNAKE enum values to Title Case", () => {
    expect(formatAuditMessage("Client Type:DIRECT_ADVERTISER")).toBe(
      "Client Type: Direct Advertiser",
    );
  });

  it("leaves single all-caps tokens (currency, acronyms) untouched", () => {
    expect(formatAuditMessage("Avg CPM Cost: BRL 59.08")).toBe(
      "Avg CPM Cost: BRL 59.08",
    );
  });

  it("adds a space after colons that lack one", () => {
    expect(formatAuditMessage("Dates:08/07/2026 - 07/08/2026")).toBe(
      "Dates: 08/07/2026 - 07/08/2026",
    );
  });

  it("does not reorder ambiguous dates", () => {
    expect(formatAuditMessage("Dates: 08/07/2026")).toBe("Dates: 08/07/2026");
  });

  it("passes through already-clean messages unchanged", () => {
    expect(formatAuditMessage("Campaign finalized by John Doe")).toBe(
      "Campaign finalized by John Doe",
    );
  });

  it("returns empty / falsy input unchanged", () => {
    expect(formatAuditMessage("")).toBe("");
  });
});
