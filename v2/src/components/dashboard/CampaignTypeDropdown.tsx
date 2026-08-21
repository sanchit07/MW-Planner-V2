import {
  Dropdown,
  DropdownContent,
  DropdownItem,
  DropdownTrigger,
} from "@components/ui/Dropdown";
import { Label } from "@components/ui/Label";
import { useTranslate } from "@tolgee/react";
import React from "react";
import { type CampaignType } from "src/types/campaign";

export interface CampaignTypeDropdownProps {
  value: CampaignType;
  onChange: (value: CampaignType) => void;
  showLabel?: boolean;
  className?: string;
  triggerClassName?: string;
}

const CampaignTypeDropdown: React.FC<CampaignTypeDropdownProps> = ({
  value,
  onChange,
  showLabel = true,
  className,
  triggerClassName,
}) => {
  const { t: tDashboard } = useTranslate(["dashboard"]);

  const handleChange = (newValue: string) => {
    onChange(newValue as CampaignType);
  };

  return (
    <div className={`flex items-center gap-2 ${className || ""}`}>
      {showLabel && (
        <Label className="text-sm font-normal text-mw-neutral-700">
          {tDashboard("campaignTypeDropdown.label")}
        </Label>
      )}
      <Dropdown value={value} onChange={handleChange}>
        <DropdownTrigger
          className={triggerClassName || "min-w-[180px] justify-between"}
        >
          {tDashboard(`campaignTypeDropdown.${value}`)}
        </DropdownTrigger>
        <DropdownContent>
          <DropdownItem value="all-campaign">
            {tDashboard("campaignTypeDropdown.all-campaign")}
          </DropdownItem>
          {/* <DropdownItem value="all-programmatic ">
            All Programmatic
          </DropdownItem>
          <DropdownItem value="programmatic -guaranteed">
            Programmatic Guaranteed
          </DropdownItem>
          <DropdownItem value="preferred-deal">Preferred Deal</DropdownItem>
          <DropdownItem value="private-market-place">
            Private Market Place
          </DropdownItem>
          <DropdownItem value="evergreen-pmp">Evergreen PMP</DropdownItem>
          <DropdownItem value="open-auction">Open Auction</DropdownItem> */}
        </DropdownContent>
      </Dropdown>
    </div>
  );
};

export default CampaignTypeDropdown;
