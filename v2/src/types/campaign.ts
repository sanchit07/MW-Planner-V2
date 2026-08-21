export type CampaignType =
  | "all-campaign"
  | "all-programmatic "
  | "programmatic -guaranteed"
  | "preferred-deal"
  | "private-market-place"
  | "evergreen-pmp"
  | "open-auction";

export const getCampaignTypeLabel = (type: CampaignType): string => {
  switch (type) {
    case "all-campaign":
      return "All Campaign";
    case "all-programmatic ":
      return "All Programmatic ";
    case "programmatic -guaranteed":
      return "Programmatic  Guaranteed";
    case "preferred-deal":
      return "Preferred Deal";
    case "private-market-place":
      return "Private Market Place";
    case "evergreen-pmp":
      return "Evergreen PMP";
    case "open-auction":
      return "Open Auction";
    default:
      return "All Campaign";
  }
};
