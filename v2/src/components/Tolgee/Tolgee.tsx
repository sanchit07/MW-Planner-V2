import { TolgeeConfig } from "@config/tolgee";
import { TolgeeProvider } from "@tolgee/react";
import React from "react";

interface TolgeeLoaderProps {
  children: React.ReactNode;
}
const TolgeeContextProvider: React.FC<TolgeeLoaderProps> = ({ children }) => {
  return (
    <TolgeeProvider
      tolgee={TolgeeConfig}
      fallback="Loading..." // loading fallback
    >
      {children}
    </TolgeeProvider>
  );
};

export default TolgeeContextProvider;
