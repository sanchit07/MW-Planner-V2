import { DemoModeBanner } from "@components/TestModeSwitch";
import { useAuthSync } from "@hooks/useAuthSync";
import React from "react";
import { Outlet, useLocation } from "react-router-dom";

import { Footer } from "./Footer";
import { Header } from "./Header";
import { InactivityTimer } from "./InactivityTimer";
import { Sidebar } from "./Sidebar";
import { useAppSelector } from "../store";

export const Layout: React.FC = () => {
  useAuthSync();
  const location = useLocation();
  const isSidebarCollapsed = useAppSelector(
    (s) => s.sidebar.isSidebarCollapsed,
  );

  return (
    <div className="h-screen w-screen overflow-hidden">
      <div className="h-full w-full flex flex-col">
        <Header />
        <DemoModeBanner />
        <div className="layout-content-wrapper flex flex-1 overflow-hidden">
          {/* Mobile sidebar overlay */}
          <div
            className={`md:hidden h-full fixed inset-0 z-40 ${
              !isSidebarCollapsed ? "block" : "hidden"
            }`}
          >
            <div className="fixed inset-0 bg-black bg-opacity-50" />
            <div className="fixed inset-y-0 left-0 w-64">
              <Sidebar />
            </div>
          </div>

          {/* Desktop sidebar */}
          <div className="hidden md:block h-full relative z-30">
            <Sidebar />
          </div>

          <main
            className={`flex-1 flex flex-col overflow-hidden scrollbar-thin transition-all duration-300 ease-in-out main-content`}
          >
            <div className="flex-1 overflow-hidden">
              <Outlet />
            </div>
            {location.pathname !== "/campaigns/create" &&
              !location.pathname.includes("/campaigns/edit") && <Footer />}
          </main>
        </div>
      </div>
      <InactivityTimer />
    </div>
  );
};

export default Layout;
