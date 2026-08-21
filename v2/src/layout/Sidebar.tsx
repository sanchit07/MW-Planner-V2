import { useActiveCompany } from "@hooks/useActiveCompany";
import { useGetApprovalInboxQuery } from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
/*
 * HOW TO RE-ENABLE THE CONFIGURATIONS DROPDOWN
 * ─────────────────────────────────────────────
 * Creatives, Reservations and Statements nav items are all enabled.
 *
 * TO RE-ENABLE THE CONFIGURATIONS DROPDOWN:
 * 1. Uncomment the lucide-react imports:
 *      MapPin, Settings, ChevronDown, Palette, Cog, Radio
 *
 * 2. Uncomment the React hook imports:
 *      useState, useRef, useEffect
 *
 * 3. Uncomment the react-dom import:
 *      createPortal
 *
 * 4. Uncomment the react-router-dom import:
 *      useLocation
 *
 * 5. Inside the component, uncomment:
 *      - State declarations: configOpen, configFlyoutOpen, flyoutPosition
 *      - Refs: configButtonRef, flyoutRef, flyoutTimeoutRef
 *      - The location = useLocation() line
 *      - isConfigChildActive derived value
 *      - Both useEffect blocks (auto-expand + flyout position)
 *      - handleFlyoutMouseEnter / handleFlyoutMouseLeave handlers
 *      - configMenuItems array
 *      - FlyoutMenu component definition
 *
 * 6. In the JSX, uncomment:
 *      - The "Configurations - Collapsible Menu" <div> block
 *      - The <FlyoutMenu /> portal render at the bottom of <aside>
 */

import {
  Calendar,
  CalendarCheck,
  Image,
  LayoutDashboard,
  Receipt,
  Timer,
  User,
  // MapPin,       // step 1
  // Settings,     // step 1
  // ChevronDown,  // step 1
  // Palette,      // step 1
  // Cog,          // step 1
  // Radio,        // step 1
} from "lucide-react";
import React from "react"; // step 2: add { useState, useRef, useEffect }
// import { createPortal } from "react-dom";  // step 3
import { NavLink } from "react-router-dom"; // step 4: add { useLocation }
// import { useLocation } from "react-router-dom"; // step 4

import { useAppSelector } from "../store";

export const Sidebar: React.FC = () => {
  const isCollapsed = useAppSelector((s) => s.sidebar.isSidebarCollapsed);
  // const [configOpen, setConfigOpen] = useState(false);
  // const [configFlyoutOpen, setConfigFlyoutOpen] = useState(false);
  // const [flyoutPosition, setFlyoutPosition] = useState({ top: 0, left: 0 });
  const { t: tCommon } = useTranslate(["common"]);
  const { companyId: activeCompanyId } = useActiveCompany();
  // Poll the Plan Approval inbox for the "awaiting you" badge count.
  const { data: inboxData } = useGetApprovalInboxQuery(
    { activeCompanyId },
    { pollingInterval: 60_000 },
  );
  const awaitingCount =
    inboxData?.data?.filter((item) => item.canAct).length ?? 0;
  // const location = useLocation();
  // const configButtonRef = useRef<HTMLButtonElement>(null);
  // const flyoutRef = useRef<HTMLDivElement>(null);
  // const flyoutTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // // Check if any configuration child route is active
  // const isConfigChildActive = [
  //   "/inventories",
  //   "/proposal-theme",
  //   "/settings",
  //   "/signals",
  // ].some((path) => location.pathname.startsWith(path));

  // // Auto-expand config menu if a child is active
  // useEffect(() => {
  //   if (isConfigChildActive && !configOpen) {
  //     setConfigOpen(true);
  //   }
  //   // eslint-disable-next-line react-hooks/exhaustive-deps
  // }, [isConfigChildActive]);

  // // Update flyout position when it opens
  // useEffect(() => {
  //   if (configFlyoutOpen && configButtonRef.current) {
  //     const rect = configButtonRef.current.getBoundingClientRect();
  //     setFlyoutPosition({
  //       top: rect.top,
  //       left: rect.right + 8, // 8px gap from the button
  //     });
  //   }
  // }, [configFlyoutOpen]);

  // // Handle flyout menu hover with delay
  // const handleFlyoutMouseEnter = () => {
  //   if (flyoutTimeoutRef.current) {
  //     clearTimeout(flyoutTimeoutRef.current);
  //     flyoutTimeoutRef.current = null;
  //   }
  //   setConfigFlyoutOpen(true);
  // };

  // const handleFlyoutMouseLeave = () => {
  //   flyoutTimeoutRef.current = setTimeout(() => {
  //     setConfigFlyoutOpen(false);
  //   }, 150);
  // };

  // // Configuration submenu items
  // const configMenuItems = [
  //   {
  //     id: "inventories",
  //     to: "/inventories",
  //     icon: MapPin,
  //     labelKey: "sidebar.inventories",
  //   },
  //   {
  //     id: "proposal-theme",
  //     to: "/proposal-theme",
  //     icon: Palette,
  //     labelKey: "sidebar.proposalThemes",
  //   },
  //   {
  //     id: "settings",
  //     to: "/settings",
  //     icon: Cog,
  //     labelKey: "sidebar.settings",
  //   },
  //   {
  //     id: "signals",
  //     to: "/signals",
  //     icon: Radio,
  //     labelKey: "sidebar.signals",
  //   },
  // ];

  // // Flyout menu portal component
  // const FlyoutMenu = () => {
  //   if (!configFlyoutOpen || !isCollapsed) return null;

  //   return createPortal(
  //     <div
  //       ref={flyoutRef}
  //       className="fixed bg-white dark:bg-mw-neutral-900 border border-mw-neutral-200 dark:border-mw-neutral-700 rounded-lg shadow-lg py-2 min-w-48"
  //       style={{
  //         top: flyoutPosition.top,
  //         left: flyoutPosition.left,
  //         zIndex: 9999,
  //       }}
  //       onMouseEnter={handleFlyoutMouseEnter}
  //       onMouseLeave={handleFlyoutMouseLeave}
  //     >
  //       {/* Flyout header */}
  //       <div className="px-3 py-2 border-b border-mw-neutral-100 dark:border-mw-neutral-700">
  //         <span className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200">
  //           {tCommon("sidebar.configurations")}
  //         </span>
  //       </div>
  //       {/* Flyout menu items */}
  //       <div className="py-1">
  //         {configMenuItems.map((item) => (
  //           <NavLink
  //             key={item.id}
  //             id={`sidebar-flyout-${item.id}`}
  //             to={item.to}
  //             onClick={() => setConfigFlyoutOpen(false)}
  //             className={({ isActive }) =>
  //               `flex px-3 py-2 justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary-500" : "text-mw-neutral-700 dark:text-mw-neutral-200"}`
  //             }
  //           >
  //             {({ isActive }) => (
  //               <>
  //                 <item.icon
  //                   className={`w-4 h-4 ${isActive ? "text-mw-primary-500" : ""}`}
  //                 />
  //                 <span className="text-sm leading-none">
  //                   {tCommon(item.labelKey)}
  //                 </span>
  //               </>
  //             )}
  //           </NavLink>
  //         ))}
  //       </div>
  //     </div>,
  //     document.body,
  //   );
  // };

  return (
    <aside
      id="app-sidebar"
      className={`bg-white dark:bg-mw-neutral-900 border-r border-container-border dark:border-mw-neutral-800 inline-flex flex-col ${isCollapsed ? "w-16 p-2" : "w-56 p-2"} transition-all duration-300 h-full overflow-hidden justify-start gap-2`}
    >
      <nav id="sidebar-nav" className="mt-2 space-y-1 px-2">
        {/* Dashboard */}
        <NavLink
          id="sidebar-nav-dashboard"
          to="/dashboard"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""}`
          }
        >
          {({ isActive }) => (
            <>
              <LayoutDashboard
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.dashboard")}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Campaigns */}
        <NavLink
          id="sidebar-nav-campaigns"
          to="/campaigns"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
          }
        >
          {({ isActive }) => (
            <>
              <Calendar
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.campaigns")}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Approvals */}
        <NavLink
          id="sidebar-nav-approvals"
          to="/approvals"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
          }
        >
          {({ isActive }) => (
            <>
              <CalendarCheck
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.approvals")}
                </span>
              )}
              {!isCollapsed && awaitingCount > 0 && (
                <span className="min-w-5 h-5 px-1 rounded-full bg-mw-primary-500 text-white text-[11px] leading-5 text-center">
                  {awaitingCount}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Profile */}
        <NavLink
          id="sidebar-nav-profile"
          to="/profile"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
          }
        >
          {({ isActive }) => (
            <>
              <User
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.profile")}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Creatives */}
        <NavLink
          id="sidebar-nav-creatives"
          to="/creatives"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
          }
        >
          {({ isActive }) => (
            <>
              <Image
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.creatives")}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Reservations */}
        <NavLink
          id="sidebar-nav-reservations"
          to="/reservations"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
          }
        >
          {({ isActive }) => (
            <>
              <Timer
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.reservations")}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Statements */}
        <NavLink
          id="sidebar-nav-statements"
          to="/statements"
          className={({ isActive }) =>
            `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
          }
        >
          {({ isActive }) => (
            <>
              <Receipt
                className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
              />
              {!isCollapsed && (
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.statements")}
                </span>
              )}
            </>
          )}
        </NavLink>

        {/* Configurations - Collapsible Menu (commented out, re-enable when needed) */}
        {/* <div
          className="relative"
          onMouseEnter={isCollapsed ? handleFlyoutMouseEnter : undefined}
          onMouseLeave={isCollapsed ? handleFlyoutMouseLeave : undefined}
        >
          <button
            ref={configButtonRef}
            id="sidebar-nav-configurations"
            onClick={() => !isCollapsed && setConfigOpen(!configOpen)}
            className={`flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary w-full min-w-0 ${configOpen || isConfigChildActive || configFlyoutOpen ? "bg-mw-primary-50 dark:bg-mw-neutral-800" : ""}`}
          >
            <Settings
              className={`w-4 h-4 relative overflow-hidden ${isConfigChildActive ? "text-mw-primary-500" : ""}`}
            />
            {!isCollapsed && (
              <>
                <span
                  className={`flex-1 justify-start text-mw-primary text-sm leading-none text-left ${isConfigChildActive ? "text-mw-primary-500" : ""}`}
                >
                  {tCommon("sidebar.configurations")}
                </span>
                <ChevronDown
                  className={`w-4 h-4 transition-transform ${configOpen ? "rotate-180" : ""}`}
                />
              </>
            )}
          </button>

          {!isCollapsed && configOpen && (
            <div className="ml-4 mt-1 space-y-1 pl-1">
              {configMenuItems.map((item) => (
                <NavLink
                  key={item.id}
                  id={`sidebar-nav-${item.id}`}
                  to={item.to}
                  className={({ isActive }) =>
                    `flex p-2 rounded justify-start items-center gap-2 hover:bg-mw-primary-50 hover:text-mw-primary ${isActive ? "bg-mw-primary-50 dark:bg-mw-neutral-800 text-mw-primary" : ""} min-w-0`
                  }
                >
                  {({ isActive }) => (
                    <>
                      <item.icon
                        className={`w-4 h-4 relative overflow-hidden ${isActive ? "text-mw-primary-500" : ""}`}
                      />
                      <span
                        className={`flex-1 justify-start text-mw-primary text-sm leading-none ${isActive ? "text-mw-primary-500" : ""}`}
                      >
                        {tCommon(item.labelKey)}
                      </span>
                    </>
                  )}
                </NavLink>
              ))}
            </div>
          )}
        </div> */}
      </nav>

      {/* Flyout Menu Portal (commented out, re-enable when needed) */}
      {/* <FlyoutMenu /> */}
    </aside>
  );
};

export default Sidebar;
