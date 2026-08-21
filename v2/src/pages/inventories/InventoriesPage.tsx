import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { useTranslate } from "@tolgee/react";
import React from "react";

import PageHeader from "../../components/PageHeader";

const InventoriesPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t: tInventories } = useTranslate([namespace]);

  return (
    <div id="inventories-page" className="h-full flex flex-col">
      <PageHeader
        title={tInventories("title")}
        descriptionKey={tInventories("description")}
      />
    </div>
  );
};

export default InventoriesPage;
