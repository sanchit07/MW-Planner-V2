import { MediaChannel } from "@constants/inventory.constants";
import type { BudgetAllocation } from "src/types/campaign.types";
import type { InventoryItem } from "src/types/inventory.types";

export type ChannelKey = "digital" | "classic" | "cinema";

export interface SelectedStat {
  id: string;
  estimatedCost: number;
  estimatedImpressions: number;
  inventoryType: string;
}

export interface FooterTotals {
  count: number;
  impressions: number;
  cost: number;
  overBudget: boolean;
  overBy: number;
}

export interface ChannelRow {
  channel: MediaChannel;
  key: ChannelKey;
  planned: number;
  selected: number;
  difference: number;
  inventories: number;
}

// detail.inventoryType is free-form ("Digital", "Digital Network", "Transit",
// "OOH", "Cinema", …). "cinema*" is the cinema channel, "digital*" the digital
// channel; everything else is classic (mirrors the classification prefix match
// in inventory.utils.ts).
export function channelOfType(inventoryType: string): ChannelKey {
  const it = (inventoryType ?? "").toLowerCase();
  if (it.includes("cinema")) return "cinema";
  return it.startsWith("digital") ? "digital" : "classic";
}

export function statFromItem(item: InventoryItem): SelectedStat {
  const p = item.performance;
  return {
    id: item.detail.id,
    estimatedCost: p?.estimatedCost ?? 0,
    estimatedImpressions:
      p?.estimatedImpression ?? p?.estimatedImpressions ?? 0,
    inventoryType: item.detail?.inventoryType ?? "",
  };
}

export function computeFooterTotals(
  map: Map<string, SelectedStat>,
  budget: number,
): FooterTotals {
  let impressions = 0;
  let cost = 0;
  for (const s of map.values()) {
    impressions += s.estimatedImpressions;
    cost += s.estimatedCost;
  }
  const overBy = Math.max(0, cost - budget);
  return { count: map.size, impressions, cost, overBudget: overBy > 0, overBy };
}

const CHANNEL_KEY: Record<string, ChannelKey> = {
  [MediaChannel.DIGITAL_OOH]: "digital",
  [MediaChannel.CLASSIC_OOH]: "classic",
  [MediaChannel.CINEMA]: "cinema",
};

export function computeChannelRows(
  map: Map<string, SelectedStat>,
  mediaChannels: string[],
  budgetAllocation: BudgetAllocation | undefined,
  budget: number,
): ChannelRow[] {
  const selectedByChannel: Record<ChannelKey, { cost: number; count: number }> =
    {
      digital: { cost: 0, count: 0 },
      classic: { cost: 0, count: 0 },
      cinema: { cost: 0, count: 0 },
    };
  for (const s of map.values()) {
    const k = channelOfType(s.inventoryType);
    selectedByChannel[k].cost += s.estimatedCost;
    selectedByChannel[k].count += 1;
  }
  return (mediaChannels ?? [])
    .map((ch) => CHANNEL_KEY[ch] && { ch, key: CHANNEL_KEY[ch] })
    .filter(Boolean)
    .map(({ ch, key }: { ch: string; key: ChannelKey }) => {
      const pct = budgetAllocation ? (budgetAllocation[key] ?? 0) : 0;
      const planned = (pct / 100) * (budget ?? 0);
      const selected = selectedByChannel[key].cost;
      return {
        channel: ch as MediaChannel,
        key,
        planned,
        selected,
        difference: selected - planned,
        inventories: selectedByChannel[key].count,
      };
    });
}
