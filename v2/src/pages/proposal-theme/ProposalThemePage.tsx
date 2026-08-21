import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { useTranslate } from "@tolgee/react";
import React from "react";

import PageHeader from "../../components/PageHeader";

const ProposalThemePage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t: tProposals } = useTranslate([namespace]);

  return (
    <div id="proposal-theme-page" className="h-full flex flex-col">
      <PageHeader
        title={tProposals("title")}
        descriptionKey={tProposals("description")}
      />
    </div>
  );
};

export default ProposalThemePage;
