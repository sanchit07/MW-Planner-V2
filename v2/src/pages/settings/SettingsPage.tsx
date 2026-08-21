import { useNamespace } from "@components/Tolgee/RouteNamespaceManager";
import { Button } from "@components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@components/ui/card";
import { Switch } from "@components/ui/Switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@components/ui/Tabs";
import {
  CompanyBranding,
  useGetCompanyBrandingQuery,
  useUpdateCompanyBrandingMutation,
} from "@services/companyBranding/companyBrandingSlice";
import {
  ApprovalsSettings,
  BonusWorkflowSettings,
  CampaignToggleSettings,
  DashboardSettings,
  FiltersSettings,
  GeneralSettings,
  InventoryToggleSettings,
  NumberFormatSettings,
  PlannerConfiguration,
  PoiSettings,
  ReportsSettings,
  ScheduleSettings,
  TargetingSettings,
  TerminologySettings,
  useGetPlannerConfigurationQuery,
  useUpdatePlannerConfigurationMutation,
} from "@services/plannerConfiguration/plannerConfigurationSlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import React, { useEffect, useState } from "react";

import PageHeader from "../../components/PageHeader";

const inputClass =
  "block w-full h-10 px-3 py-2 border rounded-md text-sm border-mw-neutral-200 dark:border-mw-neutral-600 text-mw-neutral-700 dark:text-mw-neutral-200 focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800";
const labelClass =
  "text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200";

/** Comma-separated list <-> string[] helper for the simple text-list fields below. */
const toList = (v: string) =>
  v
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
const fromList = (v?: string[]) => (v ?? []).join(", ");

const SettingsPage: React.FC = () => {
  const { namespace } = useNamespace();
  const { t: tSettings } = useTranslate([namespace]);

  const user = useAppSelector((state) => state.profile.profile);
  const companyId = user?.activeCompanyId || user?.current_company?.id || "";
  const isGlobalAdmin = !!user?.is_global_admin;
  const isSupplierSide = !!user?.current_company?.company_type?.is_supplier_side;
  const canEditBonusWorkflow = isGlobalAdmin || isSupplierSide;

  const { data: configResponse, isLoading } = useGetPlannerConfigurationQuery(
    { companyId },
    { skip: !companyId },
  );
  const [updateConfig, { isLoading: isSaving }] =
    useUpdatePlannerConfigurationMutation();

  const config =
    configResponse && "success" in configResponse && configResponse.success
      ? (configResponse as { data: PlannerConfiguration }).data
      : undefined;
  const [draft, setDraft] = useState<PlannerConfiguration | undefined>(config);
  useEffect(() => setDraft(config), [config]);

  const save = (section: keyof PlannerConfiguration) => {
    if (!draft) return;
    const update = {
      [section]: draft[section],
    } as Partial<PlannerConfiguration>;
    updateConfig({ companyId, update });
  };

  const setSection = <K extends keyof PlannerConfiguration>(
    section: K,
    value: PlannerConfiguration[K],
  ) =>
    setDraft((d) => (d ? { ...d, [section]: value } : d));

  const { data: brandingResponse } = useGetCompanyBrandingQuery(
    { companyId },
    { skip: !companyId || !isGlobalAdmin },
  );
  const [updateBranding, { isLoading: isSavingBranding }] =
    useUpdateCompanyBrandingMutation();
  const [brandingDraft, setBrandingDraft] = useState({
    whiteLabel: false,
    logoUrl: "",
  });
  useEffect(() => {
    if (
      brandingResponse &&
      "success" in brandingResponse &&
      brandingResponse.success
    ) {
      const data = (brandingResponse as { data: CompanyBranding }).data;
      setBrandingDraft({
        whiteLabel: data.whiteLabel,
        logoUrl: data.logoUrl ?? "",
      });
    }
  }, [brandingResponse]);

  if (isLoading || !draft) {
    return (
      <div id="settings-page" className="h-full flex flex-col">
        <PageHeader
          title={tSettings("title")}
          descriptionKey={tSettings("description")}
        />
      </div>
    );
  }

  const general = draft.general ?? {};
  const terminology = draft.terminology ?? {};
  const targeting = draft.targeting ?? {};
  const numberFormats = draft.numberFormats ?? {};
  const dashboard = draft.dashboard ?? {};
  const campaign = draft.campaign ?? {};
  const inventory = draft.inventory ?? {};
  const poi = draft.poi ?? {};
  const schedule = draft.schedule ?? {};
  const reports = draft.reports ?? {};
  const filters = draft.filters ?? {};
  const approvals = draft.approvals ?? {};
  const bonusWorkflow = draft.bonusWorkflow ?? {};

  const SectionCard: React.FC<{
    title: string;
    description?: string;
    onSave: () => void;
    saving: boolean;
    children: React.ReactNode;
  }> = ({ title, description, onSave, saving, children }) => (
    <Card>
      <CardHeader className="p-6 pb-4 border-b border-container-border">
        <CardTitle>
          <span className="text-lg font-semibold">{title}</span>
        </CardTitle>
        {description && (
          <CardDescription>
            <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 mt-1">
              {description}
            </p>
          </CardDescription>
        )}
      </CardHeader>
      <CardContent className="p-6 pt-6 space-y-5">
        {children}
        <div className="pt-2">
          <Button
            type="button"
            variant="primary"
            size="sm"
            disabled={saving}
            onClick={onSave}
          >
            {saving
              ? tSettings("actions.saving")
              : tSettings("actions.save")}
          </Button>
        </div>
      </CardContent>
    </Card>
  );

  return (
    <div id="settings-page" className="h-full overflow-y-auto">
      <PageHeader
        title={tSettings("title")}
        descriptionKey={tSettings("description")}
      />

      <div className="max-w-[65rem] mx-auto py-6 px-6">
        <Tabs defaultValue="general">
          <TabsList className="w-full flex-wrap h-auto">
            <TabsTrigger value="general">{tSettings("tabs.general")}</TabsTrigger>
            <TabsTrigger value="terminology">{tSettings("tabs.terminology")}</TabsTrigger>
            <TabsTrigger value="targeting">{tSettings("tabs.targeting")}</TabsTrigger>
            <TabsTrigger value="numberFormats">{tSettings("tabs.numberFormats")}</TabsTrigger>
            <TabsTrigger value="dashboard">{tSettings("tabs.dashboard")}</TabsTrigger>
            <TabsTrigger value="campaign">{tSettings("tabs.campaign")}</TabsTrigger>
            <TabsTrigger value="inventory">{tSettings("tabs.inventory")}</TabsTrigger>
            <TabsTrigger value="poi">{tSettings("tabs.poi")}</TabsTrigger>
            <TabsTrigger value="schedule">{tSettings("tabs.schedule")}</TabsTrigger>
            <TabsTrigger value="reports">{tSettings("tabs.reports")}</TabsTrigger>
            <TabsTrigger value="filters">{tSettings("tabs.filters")}</TabsTrigger>
            <TabsTrigger value="approvals">{tSettings("tabs.approvals")}</TabsTrigger>
            {canEditBonusWorkflow && (
              <TabsTrigger value="bonusWorkflow">
                {tSettings("tabs.bonusWorkflow")}
              </TabsTrigger>
            )}
            {isGlobalAdmin && (
              <TabsTrigger value="branding">{tSettings("tabs.branding")}</TabsTrigger>
            )}
          </TabsList>

          <TabsContent value="general" className="mt-4">
            <SectionCard
              title={tSettings("tabs.general")}
              onSave={() => save("general")}
              saving={isSaving}
            >
              <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("general.dateFormat")}</label>
                  <input
                    className={inputClass}
                    value={general.dateFormat ?? ""}
                    onChange={(e) =>
                      setSection("general", {
                        ...general,
                        dateFormat: e.target.value,
                      } as GeneralSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("general.timeFormat")}</label>
                  <input
                    className={inputClass}
                    value={general.timeFormat ?? ""}
                    onChange={(e) =>
                      setSection("general", {
                        ...general,
                        timeFormat: e.target.value,
                      } as GeneralSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("general.currencyDisplay")}
                  </label>
                  <select
                    className={inputClass}
                    value={general.currencyDisplay ?? "CODE"}
                    onChange={(e) =>
                      setSection("general", {
                        ...general,
                        currencyDisplay: e.target.value as "CODE" | "SYMBOL",
                      } as GeneralSettings)
                    }
                  >
                    <option value="CODE">{tSettings("general.currencyCode")}</option>
                    <option value="SYMBOL">{tSettings("general.currencySymbol")}</option>
                  </select>
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("general.fiscalYearStartMonth")}
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={12}
                    className={inputClass}
                    value={general.fiscalYearStartMonth ?? 1}
                    onChange={(e) =>
                      setSection("general", {
                        ...general,
                        fiscalYearStartMonth: Number(e.target.value),
                      } as GeneralSettings)
                    }
                  />
                </div>
              </div>
              <div className="flex items-center justify-between pt-2">
                <span className={labelClass}>{tSettings("general.helpBubbles")}</span>
                <Switch
                  checked={!!general.helpBubblesEnabled}
                  onChange={(checked) =>
                    setSection("general", {
                      ...general,
                      helpBubblesEnabled: checked,
                    } as GeneralSettings)
                  }
                />
              </div>
              <div className="flex items-center justify-between">
                <span className={labelClass}>{tSettings("general.tour")}</span>
                <Switch
                  checked={!!general.tourEnabled}
                  onChange={(checked) =>
                    setSection("general", {
                      ...general,
                      tourEnabled: checked,
                    } as GeneralSettings)
                  }
                />
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="terminology" className="mt-4">
            <SectionCard
              title={tSettings("tabs.terminology")}
              description={tSettings("terminology.description")}
              onSave={() => save("terminology")}
              saving={isSaving}
            >
              <div className="space-y-1">
                <label className={labelClass}>{tSettings("terminology.campaign")}</label>
                <input
                  className={inputClass}
                  value={terminology.customTerms?.campaign ?? ""}
                  onChange={(e) =>
                    setSection("terminology", {
                      customTerms: {
                        ...terminology.customTerms,
                        campaign: e.target.value,
                      },
                    } as TerminologySettings)
                  }
                />
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="targeting" className="mt-4">
            <SectionCard
              title={tSettings("tabs.targeting")}
              onSave={() => save("targeting")}
              saving={isSaving}
            >
              <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("targeting.ageGroupRanges")}</label>
                  <input
                    className={inputClass}
                    value={fromList(targeting.ageGroupRanges)}
                    onChange={(e) =>
                      setSection("targeting", {
                        ...targeting,
                        ageGroupRanges: toList(e.target.value),
                      } as TargetingSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("targeting.incomeBrackets")}</label>
                  <input
                    className={inputClass}
                    value={fromList(targeting.incomeBrackets)}
                    onChange={(e) =>
                      setSection("targeting", {
                        ...targeting,
                        incomeBrackets: toList(e.target.value),
                      } as TargetingSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("targeting.radiusUnit")}</label>
                  <select
                    className={inputClass}
                    value={targeting.radiusUnit ?? "km"}
                    onChange={(e) =>
                      setSection("targeting", {
                        ...targeting,
                        radiusUnit: e.target.value,
                      } as TargetingSettings)
                    }
                  >
                    <option value="km">km</option>
                    <option value="mi">mi</option>
                  </select>
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("targeting.defaultRadius")}</label>
                  <input
                    type="number"
                    className={inputClass}
                    value={targeting.defaultRadius ?? 0}
                    onChange={(e) =>
                      setSection("targeting", {
                        ...targeting,
                        defaultRadius: Number(e.target.value),
                      } as TargetingSettings)
                    }
                  />
                </div>
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="numberFormats" className="mt-4">
            <SectionCard
              title={tSettings("tabs.numberFormats")}
              onSave={() => save("numberFormats")}
              saving={isSaving}
            >
              <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("numberFormats.thousandsSeparator")}
                  </label>
                  <input
                    className={inputClass}
                    value={numberFormats.thousandsSeparator ?? ""}
                    onChange={(e) =>
                      setSection("numberFormats", {
                        ...numberFormats,
                        thousandsSeparator: e.target.value,
                      } as NumberFormatSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("numberFormats.decimalSeparator")}
                  </label>
                  <input
                    className={inputClass}
                    value={numberFormats.decimalSeparator ?? ""}
                    onChange={(e) =>
                      setSection("numberFormats", {
                        ...numberFormats,
                        decimalSeparator: e.target.value,
                      } as NumberFormatSettings)
                    }
                  />
                </div>
              </div>
              <div className="flex items-center justify-between pt-2">
                <span className={labelClass}>
                  {tSettings("numberFormats.compactNotation")}
                </span>
                <Switch
                  checked={!!numberFormats.compactNotation}
                  onChange={(checked) =>
                    setSection("numberFormats", {
                      ...numberFormats,
                      compactNotation: checked,
                    } as NumberFormatSettings)
                  }
                />
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="dashboard" className="mt-4">
            <SectionCard
              title={tSettings("tabs.dashboard")}
              onSave={() => save("dashboard")}
              saving={isSaving}
            >
              <div className="space-y-1">
                <label className={labelClass}>{tSettings("dashboard.visibleWidgets")}</label>
                <input
                  className={inputClass}
                  value={fromList(dashboard.visibleWidgetKeys)}
                  onChange={(e) =>
                    setSection("dashboard", {
                      ...dashboard,
                      visibleWidgetKeys: toList(e.target.value),
                    } as DashboardSettings)
                  }
                />
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="campaign" className="mt-4">
            <SectionCard
              title={tSettings("tabs.campaign")}
              onSave={() => save("campaign")}
              saving={isSaving}
            >
              {(
                [
                  ["setupFeaturesEnabled", "campaign.setupFeatures"],
                  ["targetingFeaturesEnabled", "campaign.targetingFeatures"],
                  ["advancedFeaturesEnabled", "campaign.advancedFeatures"],
                ] as const
              ).map(([key, labelKey]) => (
                <div key={key} className="flex items-center justify-between">
                  <span className={labelClass}>{tSettings(labelKey)}</span>
                  <Switch
                    checked={!!campaign[key]}
                    onChange={(checked) =>
                      setSection("campaign", {
                        ...campaign,
                        [key]: checked,
                      } as CampaignToggleSettings)
                    }
                  />
                </div>
              ))}
            </SectionCard>
          </TabsContent>

          <TabsContent value="inventory" className="mt-4">
            <SectionCard
              title={tSettings("tabs.inventory")}
              onSave={() => save("inventory")}
              saving={isSaving}
            >
              <div className="space-y-1">
                <label className={labelClass}>{tSettings("inventory.visibleColumns")}</label>
                <input
                  className={inputClass}
                  value={fromList(inventory.visibleColumns)}
                  onChange={(e) =>
                    setSection("inventory", {
                      ...inventory,
                      visibleColumns: toList(e.target.value),
                    } as InventoryToggleSettings)
                  }
                />
              </div>
              <div className="space-y-1">
                <label className={labelClass}>{tSettings("inventory.visibleFilters")}</label>
                <input
                  className={inputClass}
                  value={fromList(inventory.visibleFilters)}
                  onChange={(e) =>
                    setSection("inventory", {
                      ...inventory,
                      visibleFilters: toList(e.target.value),
                    } as InventoryToggleSettings)
                  }
                />
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="poi" className="mt-4">
            <SectionCard
              title={tSettings("tabs.poi")}
              onSave={() => save("poi")}
              saving={isSaving}
            >
              <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("poi.maxPerCampaign")}</label>
                  <input
                    type="number"
                    className={inputClass}
                    value={poi.maxPoiPerCampaign ?? 0}
                    onChange={(e) =>
                      setSection("poi", {
                        ...poi,
                        maxPoiPerCampaign: Number(e.target.value),
                      } as PoiSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("poi.visibilityScope")}</label>
                  <select
                    className={inputClass}
                    value={poi.visibilityScope ?? "COMPANY"}
                    onChange={(e) =>
                      setSection("poi", {
                        ...poi,
                        visibilityScope: e.target.value as "COMPANY" | "USER",
                      } as PoiSettings)
                    }
                  >
                    <option value="COMPANY">{tSettings("poi.company")}</option>
                    <option value="USER">{tSettings("poi.user")}</option>
                  </select>
                </div>
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="schedule" className="mt-4">
            <SectionCard
              title={tSettings("tabs.schedule")}
              onSave={() => save("schedule")}
              saving={isSaving}
            >
              <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("schedule.frequencyCap")}</label>
                  <input
                    type="number"
                    className={inputClass}
                    value={schedule.frequencyCap ?? ""}
                    onChange={(e) =>
                      setSection("schedule", {
                        ...schedule,
                        frequencyCap: e.target.value
                          ? Number(e.target.value)
                          : null,
                      } as ScheduleSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("schedule.sovDefault")}</label>
                  <input
                    type="number"
                    className={inputClass}
                    value={schedule.shareOfVoiceDefault ?? 100}
                    onChange={(e) =>
                      setSection("schedule", {
                        ...schedule,
                        shareOfVoiceDefault: Number(e.target.value),
                      } as ScheduleSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>{tSettings("schedule.spotDuration")}</label>
                  <input
                    type="number"
                    className={inputClass}
                    value={schedule.spotDurationSeconds ?? 15}
                    onChange={(e) =>
                      setSection("schedule", {
                        ...schedule,
                        spotDurationSeconds: Number(e.target.value),
                      } as ScheduleSettings)
                    }
                  />
                </div>
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="reports" className="mt-4">
            <SectionCard
              title={tSettings("tabs.reports")}
              onSave={() => save("reports")}
              saving={isSaving}
            >
              <div className="space-y-1">
                <label className={labelClass}>{tSettings("reports.defaultExportFormat")}</label>
                <select
                  className={inputClass}
                  value={reports.defaultExportFormat ?? "xlsx"}
                  onChange={(e) =>
                    setSection("reports", {
                      ...reports,
                      defaultExportFormat: e.target.value,
                    } as ReportsSettings)
                  }
                >
                  <option value="xlsx">Excel (.xlsx)</option>
                  <option value="pdf">PDF</option>
                  <option value="csv">CSV</option>
                </select>
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="filters" className="mt-4">
            <SectionCard
              title={tSettings("tabs.filters")}
              onSave={() => save("filters")}
              saving={isSaving}
            >
              <div className="space-y-1">
                <label className={labelClass}>{tSettings("filters.pinned")}</label>
                <input
                  className={inputClass}
                  value={fromList(filters.pinnedFilterKeys)}
                  onChange={(e) =>
                    setSection("filters", {
                      pinnedFilterKeys: toList(e.target.value),
                    } as FiltersSettings)
                  }
                />
              </div>
            </SectionCard>
          </TabsContent>

          <TabsContent value="approvals" className="mt-4">
            <SectionCard
              title={tSettings("tabs.approvals")}
              onSave={() => save("approvals")}
              saving={isSaving}
            >
              <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("approvals.autoApproveHours")}
                  </label>
                  <input
                    type="number"
                    className={inputClass}
                    value={approvals.mediaOwnerAutoApproveHours ?? 72}
                    onChange={(e) =>
                      setSection("approvals", {
                        ...approvals,
                        mediaOwnerAutoApproveHours: Number(e.target.value),
                      } as ApprovalsSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("approvals.reminderBeforeHours")}
                  </label>
                  <input
                    type="number"
                    className={inputClass}
                    value={approvals.reminderBeforeHours ?? 48}
                    onChange={(e) =>
                      setSection("approvals", {
                        ...approvals,
                        reminderBeforeHours: Number(e.target.value),
                      } as ApprovalsSettings)
                    }
                  />
                </div>
              </div>
            </SectionCard>
          </TabsContent>

          {canEditBonusWorkflow && (
            <TabsContent value="bonusWorkflow" className="mt-4">
              <SectionCard
                title={tSettings("tabs.bonusWorkflow")}
                description={tSettings("bonusWorkflow.description")}
                onSave={() => save("bonusWorkflow")}
                saving={isSaving}
              >
                <div className="flex items-center justify-between">
                  <span className={labelClass}>{tSettings("bonusWorkflow.enabled")}</span>
                  <Switch
                    checked={!!bonusWorkflow.enabled}
                    onChange={(checked) =>
                      setSection("bonusWorkflow", {
                        ...bonusWorkflow,
                        enabled: checked,
                      } as BonusWorkflowSettings)
                    }
                  />
                </div>
                <div className="space-y-1">
                  <label className={labelClass}>
                    {tSettings("bonusWorkflow.allowedTypes")}
                  </label>
                  <input
                    className={inputClass}
                    value={fromList(bonusWorkflow.allowedBonusTypes)}
                    onChange={(e) =>
                      setSection("bonusWorkflow", {
                        ...bonusWorkflow,
                        allowedBonusTypes: toList(e.target.value),
                      } as BonusWorkflowSettings)
                    }
                  />
                </div>
              </SectionCard>
            </TabsContent>
          )}

          {isGlobalAdmin && (
            <TabsContent value="branding" className="mt-4">
              <Card>
                <CardHeader className="p-6 pb-4 border-b border-container-border">
                  <CardTitle>
                    <span className="text-lg font-semibold">
                      {tSettings("tabs.branding")}
                    </span>
                  </CardTitle>
                  <CardDescription>
                    <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 mt-1">
                      {tSettings("branding.description")}
                    </p>
                  </CardDescription>
                </CardHeader>
                <CardContent className="p-6 pt-6 space-y-5">
                  <div className="flex items-center justify-between">
                    <span className={labelClass}>{tSettings("branding.whiteLabel")}</span>
                    <Switch
                      checked={brandingDraft.whiteLabel}
                      onChange={(checked) =>
                        setBrandingDraft((b) => ({ ...b, whiteLabel: checked }))
                      }
                    />
                  </div>
                  <div className="space-y-1">
                    <label className={labelClass}>{tSettings("branding.logoUrl")}</label>
                    <input
                      className={inputClass}
                      value={brandingDraft.logoUrl}
                      onChange={(e) =>
                        setBrandingDraft((b) => ({ ...b, logoUrl: e.target.value }))
                      }
                    />
                  </div>
                  <div className="pt-2">
                    <Button
                      type="button"
                      variant="primary"
                      size="sm"
                      disabled={isSavingBranding}
                      onClick={() =>
                        updateBranding({
                          companyId,
                          update: {
                            whiteLabel: brandingDraft.whiteLabel,
                            logoUrl: brandingDraft.logoUrl || null,
                          },
                        })
                      }
                    >
                      {isSavingBranding
                        ? tSettings("actions.saving")
                        : tSettings("actions.save")}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
          )}
        </Tabs>
      </div>
    </div>
  );
};

export default SettingsPage;
