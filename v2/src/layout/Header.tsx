import { Input } from "@components/ui/Input";
import { useAnnounce } from "@hooks/useAnnounce";
import { shutdown as intercomShutdown } from "@intercom/messenger-js-sdk";
import { useGetChildCompaniesQuery } from "@services/agency/agencySlice";
import { logout, useLazyLogoutQuery } from "@services/auth/authSlice";
import {
  clearSelection,
  resetFilters as resetCampaignsUIFilters,
} from "@services/campaign/campaignsUISlice";
import { clearInventoryFilters } from "@services/stepper/stepperSlice";
import { resetCompanyScopedApiState } from "@services/companyScopedApis";
import { updateUserProfile } from "@services/user/userSlice";
import { useTranslate } from "@tolgee/react";
import { Eye, X } from "lucide-react";
import {
  // User,      // re-enable with Profile menu item
  // Settings,  // re-enable with Settings menu item
  LogOut,
  PanelLeftOpen,
  PanelLeftClose,
  Info,
  ChevronDown,
  ChevronUp,
  Loader2,
  Repeat,
} from "lucide-react";
import React, { useState, useEffect, useMemo, useRef } from "react";

import movingWallsLogo from "/img/MW-logo-trans_1754045676555.png";
import { getUserInitials } from "./headerUtils";
import { useAppDispatch, useAppSelector } from "../store";
import { TestModeSwitch } from "@components/TestModeSwitch";

import LanguageSwitcher from "./LanguageSwitcher";
import ProductSwitcher from "./ProductSwitcher";
import {
  Dropdown,
  DropdownTrigger,
  DropdownContent,
  // DropdownItem,  // re-enable with Profile menu item
} from "../components/ui/Dropdown";
import { Tooltip } from "../components/ui/Tooltip";
import { toggleSidebar } from "../services/sidebar-toggle/sidebar-toggle.slice";

interface CompanyOption {
  id: string;
  name: string;
  role_name: string;
  isChild?: boolean;
}

interface CompanySelectorProps {
  companyList: CompanyOption[];
  selectedCompany: CompanyOption | null;
  onSelect: (companyId: string) => void;
  isLoadingCompanies?: boolean;
}

const CompanySelector: React.FC<CompanySelectorProps> = ({
  companyList,
  selectedCompany,
  onSelect,
  isLoadingCompanies,
}) => {
  const { t: tCommon } = useTranslate(["common"]);
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => searchRef.current?.focus(), 0);
    } else {
      setSearch("");
    }
  }, [isOpen]);

  const filtered = useMemo(() => {
    if (!search.trim()) return companyList;
    const q = search.toLowerCase();
    return companyList.filter((c) => c.name.toLowerCase().includes(q));
  }, [companyList, search]);

  return (
    <div className="relative w-full">
      <button
        id="company-selector-trigger"
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="w-full h-10 inline-flex items-center justify-between rounded-md px-3 py-2 text-sm font-medium bg-white dark:bg-mw-neutral-800 border border-mw-neutral-100 dark:border-mw-neutral-600 text-mw-neutral-500 dark:text-mw-neutral-300 cursor-pointer hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700"
      >
        <span id="selected-company-name" className="truncate">
          {selectedCompany?.name}
        </span>
        {isLoadingCompanies ? (
          <Loader2 className="h-4 w-4 shrink-0 animate-spin" />
        ) : isOpen ? (
          <ChevronUp className="h-4 w-4 shrink-0" />
        ) : (
          <ChevronDown className="h-4 w-4 shrink-0" />
        )}
      </button>
      {isOpen && (
        <div className="absolute z-10 w-full mt-1 bg-white dark:bg-mw-neutral-800 border border-mw-neutral-100 dark:border-mw-neutral-700 rounded-md shadow-lg">
          <div className="p-2 border-b border-mw-neutral-100 dark:border-mw-neutral-700">
            <input
              ref={searchRef}
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search companies..."
              className="w-full px-2 py-1.5 text-sm rounded border border-mw-neutral-200 dark:border-mw-neutral-600 bg-white dark:bg-mw-neutral-900 text-mw-neutral-700 dark:text-mw-neutral-200 placeholder-mw-neutral-400 outline-none focus:border-mw-primary-400"
            />
          </div>
          <div className="max-h-[200px] overflow-y-auto scrollbar-thin">
            {filtered.length === 0 ? (
              <p className="px-3 py-4 text-sm text-center text-mw-neutral-400">
                No companies found
              </p>
            ) : (
              filtered.map((company) => (
                <button
                  key={company.id}
                  type="button"
                  onClick={() => {
                    onSelect(company.id);
                    setIsOpen(false);
                  }}
                  className={`w-full text-left px-3 py-2 text-sm cursor-pointer hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 flex items-center justify-between gap-2 ${
                    selectedCompany?.id === company.id
                      ? "bg-mw-primary-50 text-mw-primary-600"
                      : "text-mw-neutral-500 dark:text-mw-neutral-300"
                  }`}
                >
                  <span className="truncate">{company.name}</span>
                  {company.isChild && (
                    <span className="shrink-0 rounded-full bg-amber-100 px-3 py-1.5 text-xs font-medium text-amber-800">
                      {tCommon("header.childCompany")}
                    </span>
                  )}
                </button>
              ))
            )}
            {isLoadingCompanies && (
              <div className="flex items-center gap-2 px-3 py-2 text-xs text-mw-neutral-400 border-t border-mw-neutral-100 dark:border-mw-neutral-700">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                <span>{tCommon("header.loadingCompanies")}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export const Header: React.FC = () => {
  const dispatch = useAppDispatch();
  const [userLogout] = useLazyLogoutQuery();
  const user = useAppSelector((s) => s.profile.profile);
  const isFetchingCompanies = useAppSelector(
    (s) => s.profile.isFetchingCompanies,
  );
  const { data: childCompaniesData, isFetching: isFetchingChildCompanies } =
    useGetChildCompaniesQuery(
      { id: user?.current_company?.id },
      { skip: !user?.current_company?.id },
    );
  const isLoadingCompanies = isFetchingCompanies || isFetchingChildCompanies;
  const [selectedCompany, setSelectedCompany] = useState<CompanyOption | null>(
    null,
  );
  const [isImpersonating, setIsImpersonating] = useState(false);
  const { showSuccess } = useAnnounce();
  const { t: tCommon } = useTranslate(["common"]);

  // Build normalized company list: current_company first, then memberships, then child companies — all deduplicated
  const companyList: CompanyOption[] = useMemo(() => {
    if (!user) return [];
    const currentCompany = user.current_company
      ? {
          id: user.current_company.id,
          name: user.current_company.name,
          role_name: user.current_company.role_name,
        }
      : null;

    const childCompanyIds = new Set(
      (childCompaniesData?.children ?? [])
        .map((c) => c.company?.id)
        .filter((id): id is string => Boolean(id)),
    );

    const memberships = user.memberships
      .filter((m) => m.company_id !== currentCompany?.id)
      .map((m) => ({
        id: m.company_id,
        name: m.company_name,
        role_name: m.role_name,
        company_type: m.company_type,
        isChild: childCompanyIds.has(m.company_id) || undefined,
      }));

    const seenIds = new Set<string>([
      ...(currentCompany ? [currentCompany.id] : []),
      ...memberships.map((m) => m.id),
    ]);

    const childCompanies = (childCompaniesData?.children ?? [])
      .filter((c) => c.company && !seenIds.has(c.company.id))
      .map((c) => ({
        id: c.company!.id,
        name: c.company!.name,
        role_name: "",
        isChild: true,
      }));

    return currentCompany
      ? [currentCompany, ...memberships, ...childCompanies]
      : [...memberships, ...childCompanies];
  }, [user, childCompaniesData]);

  const getSelectedCompany = (companyId: string | null) => {
    if (companyId === user?.current_company?.id) {
      setIsImpersonating(false);
    } else {
      setIsImpersonating(true);
    }

    const id = companyId || user?.current_company?.id || null;
    if (id) {
      const company = companyList.find((c) => c.id === id) || null;
      setSelectedCompany(company);
      if (id !== user?.activeCompanyId) {
        dispatch(updateUserProfile({ activeCompanyId: id }));
        // Filters are scoped to the previously active company's data — clear
        // them so switching companies doesn't leak stale filters/selections.
        dispatch(resetCampaignsUIFilters());
        dispatch(clearSelection());
        dispatch(clearInventoryFilters());
        // Drop every company-scoped RTK Query cache: many endpoints are keyed
        // by campaign ID only, so without this a dual-member user switching
        // companies could be served the previous company's cached responses
        // (e.g. buyer-only execution plan data) without any backend call.
        resetCompanyScopedApiState(dispatch);
      }
    }
  };

  const isSidebarCollapsed = useAppSelector(
    (s) => s.sidebar.isSidebarCollapsed,
  );

  const refreshToken = useAppSelector((state) => state.auth.refreshToken);

  const handleCompanyChange = (companyId: string) => {
    getSelectedCompany(companyId);
  };

  const handleLogout = async () => {
    // Clear the Intercom session before tearing down auth to avoid leaking
    // conversation history between users on a shared device.
    intercomShutdown();
    const response = await userLogout({
      refresh_token: refreshToken || "",
    });
    if (response.data?.success) {
      showSuccess(response.data?.data || tCommon("header.logoutSuccess"));
      document.cookie.split(";").forEach(function (cookie) {
        const cookieName = cookie.split("=")[0].trim();
        document.cookie =
          cookieName + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/";
      });
      dispatch(logout());
    }
  };

  useEffect(() => {
    getSelectedCompany(user?.activeCompanyId ?? null);

    // getSelectedCompany is intentionally excluded — it is defined inline and
    // would cause infinite re-renders if added to deps. user is the only real dependency.
  }, [user]);

  return (
    <header
      id="app-header"
      className="h-18 w-full bg-white border border-container-border flex items-center justify-between p-4"
    >
      <div className="flex items-center gap-2.5">
        <ProductSwitcher />
        <div id="header-logo" className="flex items-center gap-3">
          <img
            id="header-logo-img"
            src={movingWallsLogo}
            alt="Moving Walls"
            className="h-20 w-auto"
          />
          <span
            id="header-logo-text"
            className="text-lg text-black dark:text-white font-medium"
          >
            {tCommon("header.planner")}
          </span>
        </div>
        {isSidebarCollapsed ? (
          <PanelLeftOpen
            id="sidebar-toggle-open"
            className="h-7 w-7 text-mw-neutral-400 dark:text-mw-neutral-300 cursor-pointer hover:text-mw-neutral-600 dark:hover:text-mw-neutral-100 transition-colors"
            onClick={() => dispatch(toggleSidebar())}
          />
        ) : (
          <PanelLeftClose
            id="sidebar-toggle-close"
            className="h-7 w-7 text-mw-neutral-400 dark:text-mw-neutral-300 cursor-pointer hover:text-mw-neutral-600 dark:hover:text-mw-neutral-100 transition-colors"
            onClick={() => dispatch(toggleSidebar())}
          />
        )}
      </div>
      <div id="header-right" className="flex items-center gap-4">
        {isImpersonating && (
          <div>
            <button
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-amber-100 text-amber-800 text-xs font-medium hover:bg-amber-200 transition-colors cursor-pointer"
              onClick={handleCompanyChange.bind(
                null,
                user?.current_company?.id || "",
              )}
            >
              <Eye size={14} />
              <span>
                {tCommon("header.impersonating", {
                  company: selectedCompany?.name || "",
                })}
              </span>
              <X size={12} />
            </button>
          </div>
        )}

        <TestModeSwitch />
        <LanguageSwitcher />
        <Dropdown name="user-menu">
          <DropdownTrigger
            className="flex items-center gap-3 transition-colors border-none bg-transparent pl-0"
            asChild
          >
            <div
              id="user-avatar"
              className="h-10 w-10 relative bg-mw-success-50 rounded-lg border border-container-border cursor-pointer"
            >
              <div className="left-[11px] top-[11px] absolute text-center justify-center text-black text-sm font-medium leading-none cursor-pointer">
                {getUserInitials(user?.first_name, user?.last_name)}
              </div>
            </div>
            <div
              id="user-info"
              className="text-left hidden sm:block ml-2 w-32 cursor-pointer"
            >
              <div
                id="user-name"
                className="text-sm font-medium text-mw-black dark:text-white leading-5 cursor-pointer"
              >
                {user
                  ? user?.first_name + " " + user?.last_name
                  : tCommon("header.guest")}
              </div>
              <div
                id="user-role"
                className="text-xs text-secondary leading-4 dark:text-mw-neutral-400 cursor-pointer"
              >
                {companyList.find(
                  (c) =>
                    c.id ===
                    (user?.activeCompanyId || user?.current_company?.id),
                )?.role_name ||
                  user?.current_company?.role_name ||
                  tCommon("labels.na")}
              </div>
            </div>
            <ChevronDown className="size-5 text-Background-Inverse relative overflow-hidden cursor-pointer" />
          </DropdownTrigger>

          <DropdownContent align="right" className="p-2" minWidth="300px">
            {/* Profile Section (commented out, re-enable when needed) */}
            {/* <DropdownItem value="profile">
              <User className="h-5 w-5 relative overflow-hidden" />
              <div className="justify-center text-mw-neutral-800 text-base font-normal leading-loose">
                {tCommon("header.profile")}
              </div>
            </DropdownItem> */}

            {/* Company Section */}
            <div
              id="user-menu-company-section"
              className="self-stretch px-2 py-1 rounded-sm"
            >
              <div
                className="space-y-2 w-full"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="flex items-center gap-2 text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
                  <span>{tCommon("header.company")}</span>
                  <Tooltip
                    className="border border-mw-neutral-100 dark:border-mw-neutral-600 rounded-full text-mw-neutral-600! bg-white dark:bg-mw-neutral-800"
                    content={tCommon("header.companyTooltip")}
                    position="top"
                  >
                    <Info className="w-4 h-4 relative overflow-hidden" />
                  </Tooltip>
                </div>
                <CompanySelector
                  companyList={companyList}
                  selectedCompany={selectedCompany}
                  onSelect={handleCompanyChange}
                  isLoadingCompanies={isLoadingCompanies}
                />
              </div>
            </div>

            {/* Role Section */}
            <div
              id="user-menu-role-section"
              className="self-stretch px-2 py-1 rounded-sm"
            >
              <div className=" space-y-2 w-full">
                <Input
                  id="user-role-input"
                  label={tCommon("header.role")}
                  value={user?.current_company?.role_name || ""}
                  disabled
                />
              </div>
            </div>

            {/* Settings */}
            <div
              id="user-menu-actions"
              className="self-stretch px-2 py-1 rounded-sm"
            >
              <div className="space-y-2">
                {/* Settings button (commented out, re-enable when needed) */}
                {/* <button
                  id="user-menu-settings-btn"
                  className="w-full flex items-center gap-3 p-1 py-1 text-base font-normal text-mw-neutral-700 dark:text-mw-neutral-300 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 rounded-md transition-colors"
                >
                  <Settings className="h-5 w-5" />
                  <span>{tCommon("header.settings")}</span>
                </button> */}

                {/* Version switcher: back to the V1 prototype UI */}
                <button
                  id="user-menu-switch-v1-btn"
                  onClick={() => {
                    window.location.href = "/";
                  }}
                  className="cursor-pointer w-full flex items-center gap-3 p-1 py-1 text-base font-normal text-mw-neutral-700 dark:text-mw-neutral-300 hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 rounded-md transition-colors"
                >
                  <Repeat className="h-5 w-5" />
                  <span>Switch to V1</span>
                </button>

                {/* Logout */}
                <button
                  id="user-menu-logout-btn"
                  onClick={handleLogout}
                  className="cursor-pointer w-full flex items-center gap-3 p-1 py-1 text-base font-normal text-mw-error-500 dark:text-mw-error-400 hover:bg-mw-error-50 dark:hover:bg-mw-error-900/20 rounded-md transition-colors"
                >
                  <LogOut className="h-5 w-5" />
                  <span>{tCommon("header.logout")}</span>
                </button>
              </div>
            </div>
          </DropdownContent>
        </Dropdown>
      </div>
    </header>
  );
};

export default Header;
