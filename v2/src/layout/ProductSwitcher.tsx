import { CONFIG } from "@config/index";
import { useTranslate } from "@tolgee/react";
import { Grip } from "lucide-react";

import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownSeparator,
} from "../components/ui/Dropdown";

const PRODUCT_IDS = ["influence", "measure", "inventory", "account"] as const;
type ProductId = (typeof PRODUCT_IDS)[number];

const PRODUCT_URLS: Record<ProductId, string> = {
  influence: CONFIG.INFLUENCE_URL,
  measure: CONFIG.MEASURE_URL,
  inventory: CONFIG.INVENTORY_URL,
  account: CONFIG.ACCOUNT_URL,
};

export default function ProductSwitcher() {
  const { t: tCommon } = useTranslate(["common"]);
  return (
    <div
      id="product-switcher"
      className="p-2 bg-mw-primary-50 rounded inline-flex justify-center items-center gap-1"
    >
      <Dropdown name="product-switcher">
        <DropdownTrigger asChild>
          <Grip className="size-6 bg-mw-primary-50 text-mw-black cursor-pointer" />
        </DropdownTrigger>

        <DropdownContent align="right" className="overflow-auto max-h-98 p-2">
          {[...PRODUCT_IDS]
            .sort((a, b) =>
              tCommon(`productSwitcher.${a}.name`).localeCompare(
                tCommon(`productSwitcher.${b}.name`),
              ),
            )
            .map((id) => (
              <div
                key={id}
                className="self-stretch rounded-lg flex flex-col justify-center items-start gap-1"
              >
                <a
                  href={PRODUCT_URLS[id]}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="self-stretch rounded-lg p-2 flex flex-col justify-center items-start gap-1 cursor-pointer hover:bg-mw-primary-50 no-underline"
                >
                  <div className="justify-center hover:text-mw-primary-500 text-mw-neutral-700 text-sm font-normal leading-4 line-clamp-1">
                    {tCommon(`productSwitcher.${id}.name`)}
                  </div>
                  <div className="self-stretch justify-center hover:text-mw-primary-400 text-mw-neutral-400 text-xs font-normal leading-4 line-clamp-1">
                    {tCommon(`productSwitcher.${id}.description`)}
                  </div>
                </a>
                <DropdownSeparator />
              </div>
            ))}
        </DropdownContent>
      </Dropdown>
    </div>
  );
}
