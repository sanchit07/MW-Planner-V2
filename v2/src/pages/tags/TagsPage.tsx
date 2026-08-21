import { T } from "@tolgee/react";
import React from "react";

const TagsPage: React.FC = () => {
  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">
        <T keyName="tags.title">Tags</T>
      </h1>
      <p className="text-mw-neutral-600 dark:text-mw-neutral-300">
        <T keyName="tags.description">Manage and organize content with tags.</T>
      </p>
    </div>
  );
};

export default TagsPage;
