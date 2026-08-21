import { FormatIcu } from "@tolgee/format-icu";
import { Tolgee } from "@tolgee/react";

import { getItem } from "../utils/storage";

const getInitialLanguage = (): string => {
  const storedLanguage = getItem("mw-planner-language");
  const availableLanguages = ["en", "ja"];

  return storedLanguage && availableLanguages.includes(storedLanguage)
    ? storedLanguage
    : "en";
};

export const TolgeeConfig = Tolgee()
  .use(FormatIcu())
  .init({
    language: getInitialLanguage(),
    availableLanguages: ["en", "ja"],
    fallbackLanguage: "en",
    defaultLanguage: "en",
    defaultNs: "common",
    fallbackNs: "",
    staticData: {
      "en:common": () => import("../assets/i18n/common/en.json"),
      "ja:common": () => import("../assets/i18n/common/ja.json"),
      "en:dashboard": () => import("../assets/i18n/dashboard/en.json"),
      "ja:dashboard": () => import("../assets/i18n/dashboard/ja.json"),
      "en:campaigns": () => import("../assets/i18n/campaigns/en.json"),
      "ja:campaigns": () => import("../assets/i18n/campaigns/ja.json"),
      "en:creatives": () => import("../assets/i18n/creatives/en.json"),
      "ja:creatives": () => import("../assets/i18n/creatives/ja.json"),
      "en:inventories": () => import("../assets/i18n/inventories/en.json"),
      "ja:inventories": () => import("../assets/i18n/inventories/ja.json"),
      "en:proposals": () => import("../assets/i18n/proposals/en.json"),
      "ja:proposals": () => import("../assets/i18n/proposals/ja.json"),
      "en:reservations": () => import("../assets/i18n/reservations/en.json"),
      "ja:reservations": () => import("../assets/i18n/reservations/ja.json"),
      "en:settings": () => import("../assets/i18n/settings/en.json"),
      "ja:settings": () => import("../assets/i18n/settings/ja.json"),
      "en:signals": () => import("../assets/i18n/signals/en.json"),
      "ja:signals": () => import("../assets/i18n/signals/ja.json"),
      "en:statements": () => import("../assets/i18n/statements/en.json"),
      "ja:statements": () => import("../assets/i18n/statements/ja.json"),
      "en:tags": () => import("../assets/i18n/tags/en.json"),
      "ja:tags": () => import("../assets/i18n/tags/ja.json"),
      "en:brands": () => import("../assets/i18n/brands/en.json"),
      "ja:brands": () => import("../assets/i18n/brands/ja.json"),
      "en:price": () => import("../assets/i18n/price/en.json"),
      "ja:price": () => import("../assets/i18n/price/ja.json"),
    },
  });
