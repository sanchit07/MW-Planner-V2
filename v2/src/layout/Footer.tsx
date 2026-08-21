import { useTranslate } from "@tolgee/react";
import React from "react";

export const Footer: React.FC = () => {
  const { t: tCommon } = useTranslate(["common"]);
  return (
    <footer
      id="app-footer"
      className="h-[30px] w-full bg-white dark:bg-background border-t border-mw-neutral-100 dark:border-border flex items-center justify-center py-[7px] flex-shrink-0"
    >
      <p
        id="footer-copyright"
        className="text-xs text-mw-neutral-200 dark:text-muted-foreground text-center truncate"
      >
        {tCommon("footer.copyright", { year: new Date().getFullYear() })}
      </p>
    </footer>
  );
};

export default Footer;
