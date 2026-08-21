import { T } from "@tolgee/react";
import React from "react";

const PoisPage: React.FC = () => {
  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">
        <T keyName="pois.title">POIs</T>
      </h1>
      <p className="text-mw-neutral-600 dark:text-mw-neutral-300">
        <T keyName="pois.description">
          Manage Points of Interest with unique identifiers.
        </T>
      </p>
    </div>
  );
};

export default PoisPage;
