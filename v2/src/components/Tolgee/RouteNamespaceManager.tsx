import { useTolgee } from "@tolgee/react";
import React, { useEffect, useContext, createContext, useRef } from "react";
import { useLocation } from "react-router-dom";

// Namespace context and hook
const NamespaceContext = createContext<
  | {
      namespace: string;
      setNamespace: (ns: string) => void;
    }
  | undefined
>(undefined);

export const useNamespace = () => {
  const context = useContext(NamespaceContext);
  if (!context) {
    throw new Error("useNamespace must be used within a NamespaceProvider");
  }
  return context;
};

// Route to namespace mapping
const ROUTE_NAMESPACE_MAP: Record<string, string> = {
  "/dashboard": "dashboard",
  "/campaigns": "campaigns",
  "/campaigns/new": "campaigns",
  "/creatives": "creatives",
  "/inventories": "inventories",
  "/proposals": "proposals",
  "/reservations": "reservations",
  "/settings": "settings",
  "/signals": "signals",
  "/statements": "statements",
  "/tags": "tags",
  "/pois": "pois",
  "/profile": "profile",
};

interface RouteNamespaceManagerProps {
  children: React.ReactNode;
}

const RouteNamespaceManager: React.FC<RouteNamespaceManagerProps> = ({
  children,
}) => {
  const location = useLocation();
  const tolgee = useTolgee();
  const previousLanguage = useRef<string | null>(null);
  const previousNamespace = useRef<string | null>(null);

  // Get namespace based on current route
  const getNamespaceForRoute = (pathname: string): string => {
    // Find exact match first
    if (ROUTE_NAMESPACE_MAP[pathname]) {
      return ROUTE_NAMESPACE_MAP[pathname];
    }

    // Find partial match for nested routes
    const matchedRoute = Object.keys(ROUTE_NAMESPACE_MAP).find((route) =>
      pathname.startsWith(route),
    );

    if (matchedRoute) {
      return ROUTE_NAMESPACE_MAP[matchedRoute];
    }

    // Default to common namespace
    return "common";
  };

  const currentNamespace = getNamespaceForRoute(location.pathname);
  const currentLanguage = tolgee.getLanguage() || null;

  // Consolidated effect to handle both route changes and language changes
  useEffect(() => {
    const languageChanged =
      previousLanguage.current && previousLanguage.current !== currentLanguage;
    const namespaceChanged = previousNamespace.current !== currentNamespace;

    if (languageChanged) {
      // Language changed - namespace will be reloaded automatically by Tolgee
      // No manual cleanup needed as Tolgee handles language-specific namespaces
    } else if (namespaceChanged && previousNamespace.current) {
      // Only namespace changed - remove previous namespace
      tolgee.removeActiveNs(previousNamespace.current);
    }

    // Load current namespace if it exists
    if (namespaceChanged) {
      tolgee.addActiveNs(currentNamespace);
    }

    // Update refs
    previousLanguage.current = currentLanguage;
    previousNamespace.current = currentNamespace;
  }, [currentLanguage, currentNamespace, tolgee]);

  // Context value
  const contextValue = {
    namespace: currentNamespace,
    setNamespace: (ns: string) => {
      tolgee.addActiveNs(ns);
    },
  };

  return (
    <NamespaceContext.Provider value={contextValue}>
      {children}
    </NamespaceContext.Provider>
  );
};

export default RouteNamespaceManager;
