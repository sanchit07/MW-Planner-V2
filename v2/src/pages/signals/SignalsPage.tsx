import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { useTranslate } from "@tolgee/react";
import React from "react";

import PageHeader from "../../components/PageHeader";

const SignalsPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t: tSignals } = useTranslate([namespace]);

  return (
    <div id="signals-page" className="h-full flex flex-col">
      <PageHeader
        title={tSignals("title")}
        descriptionKey={tSignals("description")}
      />
    </div>
  );
};

export default SignalsPage;
