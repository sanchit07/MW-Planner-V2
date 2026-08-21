import { useTolgee } from "@tolgee/react";

import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  DropdownItem,
} from "../components/ui/Dropdown";
import { setItem } from "../utils/storage";

export default function LanguageSwitcher() {
  const tolgee = useTolgee(["language"]);
  const currentLanguage = tolgee.getLanguage();

  const languages = [
    { code: "en", name: "ENG", flag: "🇺🇸" },
    { code: "ja", name: "JPN", flag: "ja" },
  ];

  const currentLang =
    languages.find((lang) => lang.code === currentLanguage) || languages[0];

  const handleLanguageChange = (languageCode: string) => {
    tolgee.changeLanguage(languageCode);
    setItem("mw-planner-language", languageCode);
  };

  return (
    <div id="language-switcher">
      <Dropdown name="language">
        <DropdownTrigger className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium border-none bg-transparent hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-800">
          <span id="current-language" className="hidden sm:inline">
            {currentLang.name}
          </span>
        </DropdownTrigger>

        <DropdownContent align="right">
          {languages.map((language) => (
            <DropdownItem
              key={language.code}
              value={language.code}
              onClick={() => handleLanguageChange(language.code)}
              className={`${
                currentLanguage === language.code
                  ? "bg-mw-primary-50 dark:bg-mw-primary-900/20 text-mw-primary-600 dark:text-mw-primary-400"
                  : ""
              }`}
            >
              <div className="flex items-center gap-3">
                <span>{language.name}</span>
              </div>
            </DropdownItem>
          ))}
        </DropdownContent>
      </Dropdown>
    </div>
  );
}
