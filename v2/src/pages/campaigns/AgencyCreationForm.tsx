import { zodResolver } from "@hookform/resolvers/zod";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useCreateAgencyMutation,
  useLazyGetAgenciesQuery,
  useLinkAgencyMutation,
} from "@services/agency/agencySlice";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { Building2, Info, Loader2 } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";

import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
import { ModalDrawer } from "../../components/ui/ModalDrawer";
import {
  createAgencySchema,
  type AgencyFormData,
} from "../../schemas/agencies/agency.schema";
import { Agency } from "../../types/campaign.types";

interface AgencyCreationFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit?: (data: AgencyFormData) => void;
  onSuccess?: (agencyId: string, agencyName?: string) => void;
  companyId?: string;
  companyAgencies?: { id: string; label: string }[];
}

const AgencyCreationForm: React.FC<AgencyCreationFormProps> = ({
  isOpen,
  onClose,
  onSubmit,
  onSuccess,
  companyId,
  companyAgencies = [],
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [createAgency, { isLoading }] = useCreateAgencyMutation();
  const [linkAgency] = useLinkAgencyMutation();
  const [searchAgencies] = useLazyGetAgenciesQuery();
  const { showSuccess } = useAnnounce();

  const user = useAppSelector((state) => state.profile.profile);
  const resolvedCompanyId =
    companyId || user?.activeCompanyId || user?.current_company?.id;

  const [suggestions, setSuggestions] = useState<Agency[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [selectedAgency, setSelectedAgency] = useState<Agency | null>(null);
  const [isLinking, setIsLinking] = useState(false);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
    watch,
  } = useForm<AgencyFormData>({
    resolver: zodResolver(createAgencySchema(tCampaigns)),
    defaultValues: {
      name: "",
      companyEmail: "",
      domain: "",
    },
  });

  const agencyName = watch("name");

  // Debounced search — only active in create mode (no agency selected yet).
  // Omits company_id and searches the global agency directory instead, so an
  // agency already used by another company can be found and linked here
  // rather than creating a duplicate.
  useEffect(() => {
    if (selectedAgency) return;

    const trimmed = agencyName?.trim() ?? "";
    if (trimmed.length < 2) {
      setSuggestions([]);
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);

    searchTimerRef.current = setTimeout(async () => {
      try {
        const result = await searchAgencies({
          search: trimmed,
          all: true,
        }).unwrap();
        setSuggestions(result?.data ?? []);
      } catch {
        setSuggestions([]);
      } finally {
        setIsSearching(false);
      }
    }, 300);

    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    };
  }, [agencyName, searchAgencies, selectedAgency]);

  // Reset form when drawer closes
  useEffect(() => {
    if (!isOpen) {
      reset({
        name: "",
        companyEmail: "",
        domain: "",
      });
      setSuggestions([]);
      setIsSearching(false);
      setSelectedAgency(null);
    }
  }, [isOpen, reset]);

  // Handle drawer close with form reset
  const handleClose = () => {
    reset({
      name: "",
      companyEmail: "",
      domain: "",
    });
    setSuggestions([]);
    setSelectedAgency(null);
    onClose();
  };

  // Footer Cancel: if in link mode, revert to create mode; otherwise close drawer
  const handleCancel = () => {
    if (selectedAgency) {
      reset({
        name: "",
        companyEmail: "",
        domain: "",
      });
      setSuggestions([]);
      setSelectedAgency(null);
    } else {
      handleClose();
    }
  };

  // Clicking a suggestion enters link mode — modal title and body switch to agency details
  const handleSelectSuggestion = (agency: Agency) => {
    setSelectedAgency(agency);
    setSuggestions([]);
  };

  // Link selected agency to the current company
  const handleLinkAgency = async () => {
    if (!selectedAgency || !resolvedCompanyId) return;
    setIsLinking(true);
    try {
      await linkAgency({
        agencyData: {
          agency_id: selectedAgency.id,
          campaign_approval: "manual",
          creative_approval: "manual",
        },
        id: resolvedCompanyId,
      }).unwrap();
      showSuccess(tCampaigns("agency_form.link_agency_success"));
      if (onSuccess) onSuccess(selectedAgency.id, selectedAgency.name);
      handleClose();
    } catch (err) {
      console.error("Failed to link agency:", err);
    } finally {
      setIsLinking(false);
    }
  };

  const onFormSubmit = async (data: AgencyFormData) => {
    try {
      // Call the create agency API
      const result = await createAgency(data).unwrap();
      if (result.data?.id) {
        // Call linkAgency after agency creation
        await linkAgency({
          agencyData: {
            agency_id: result.data.id,
            campaign_approval: "manual",
            creative_approval: "manual",
          },
          id: resolvedCompanyId,
        });

        showSuccess(tCampaigns("agencyForm.success"));
        onClose();
      }

      if (onSubmit) onSubmit(data);
      if (onSuccess && result.data?.id) onSuccess(result.data.id, data.name);
    } catch (err) {
      console.error("Failed to create agency:", err);
    }
  };

  const isLinkMode = !!selectedAgency;
  const trimmedAgencyName = agencyName?.trim() ?? "";
  // Agency already linked to this company — can't create again
  const hasOwnAgency =
    !selectedAgency &&
    trimmedAgencyName.length >= 2 &&
    companyAgencies.some(
      (a) => a.label.toLowerCase() === trimmedAgencyName.toLowerCase(),
    );
  // Exact name match exists elsewhere — link instead of create
  const hasExactMatch =
    !hasOwnAgency &&
    !selectedAgency &&
    trimmedAgencyName.length >= 2 &&
    suggestions.some(
      (a) => a.name.toLowerCase() === trimmedAgencyName.toLowerCase(),
    );
  const showSuggestions =
    !hasOwnAgency &&
    !selectedAgency &&
    trimmedAgencyName.length >= 2 &&
    (isSearching || suggestions.length > 0);

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={handleClose}
      onBack={isLinkMode ? handleCancel : undefined}
      title={
        isLinkMode
          ? tCampaigns("agency_form.title_link")
          : tCampaigns("agency_form.title")
      }
      size="lg"
      footer={
        <div className="flex justify-end gap-3">
          <Button
            variant="outline"
            className="outline-mw-primary-500 text-mw-primary-500"
            onClick={handleCancel}
            disabled={isLoading || isLinking}
          >
            {tCampaigns("agency_form.cancel")}
          </Button>

          {isLinkMode ? (
            <Button
              variant="primary"
              onClick={handleLinkAgency}
              disabled={isLinking}
            >
              {isLinking
                ? tCampaigns("agency_form.linking")
                : tCampaigns("agency_form.link")}
            </Button>
          ) : (
            <Button
              variant="primary"
              onClick={handleSubmit(onFormSubmit)}
              disabled={isLoading || hasExactMatch || hasOwnAgency}
            >
              {isLoading
                ? tCampaigns("agency_form.creating") || "Creating..."
                : tCampaigns("agency_form.create")}
            </Button>
          )}
        </div>
      }
    >
      {isLinkMode ? (
        /* Link mode — compact confirmation: panel height follows the
           content instead of a fixed card + notice box, so an agency with
           no country/media owner on file never leaves a half-empty drawer. */
        <div className="pt-2">
          <div className="flex items-center gap-3.5 pb-4 border-b border-mw-neutral-100 dark:border-mw-neutral-700">
            <div className="h-14 w-14 flex-shrink-0 rounded-xl bg-gradient-to-br from-mw-primary-50 to-white dark:from-mw-primary-900/30 dark:to-mw-neutral-800 border border-mw-primary-100 dark:border-mw-primary-800 flex items-center justify-center">
              <Building2 className="h-6 w-6 text-mw-primary-500" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <p className="text-base font-semibold text-mw-neutral-900 dark:text-mw-neutral-100 truncate">
                  {selectedAgency.name}
                </p>
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-mw-success-600 dark:text-mw-success-400">
                  <span
                    className={`h-1.5 w-1.5 rounded-full ${
                      selectedAgency.activated !== false
                        ? "bg-mw-success-500"
                        : "bg-mw-neutral-400"
                    }`}
                  />
                  {selectedAgency.activated !== false
                    ? tCampaigns("agency_form.active")
                    : tCampaigns("agency_form.inactive")}
                </span>
              </div>
              {(selectedAgency.countryName ||
                selectedAgency.mediaOwnerName) && (
                <p className="text-xs text-mw-neutral-500 dark:text-mw-neutral-400 mt-0.5 truncate">
                  {[selectedAgency.countryName, selectedAgency.mediaOwnerName]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              )}
            </div>
          </div>

          {/* What linking does — a single caption line, not a big notice box */}
          <div className="flex items-start gap-2 text-sm text-mw-neutral-500 dark:text-mw-neutral-400 leading-relaxed pt-8">
            <Info className="h-4 w-4 mt-0.5 flex-shrink-0 text-mw-primary-500" />
            <p>{tCampaigns("agency_form.link_notice")}</p>
          </div>
        </div>
      ) : (
        <form>
          {/* Agency Information */}
          <div className="space-y-4 pt-2">
            {/* Agency Name + suggestions */}
            <div className="space-y-1">
              <Input
                {...register("name")}
                label={tCampaigns("agency_form.name")}
                id="name"
                placeholder={tCampaigns("agency_form.name_placeholder")}
                error={errors.name?.message}
                autoFocus
                required
              />

              {/* Already linked to this company */}
              {hasOwnAgency && (
                <p className="text-sm text-mw-error-500 mt-1">
                  {tCampaigns("agency_form.own_agency_exists")}
                </p>
              )}

              {/* Suggestions panel */}
              {showSuggestions && (
                <div className="rounded-md border border-mw-neutral-200 dark:border-mw-neutral-600 bg-white dark:bg-mw-neutral-800 shadow-sm">
                  <p className="px-3 py-2 text-xs font-medium text-mw-neutral-500 dark:text-mw-neutral-400 border-b border-mw-neutral-100 dark:border-mw-neutral-700">
                    {hasExactMatch
                      ? tCampaigns("agency_form.exact_agency_match_header")
                      : tCampaigns("agency_form.similar_agencies_header")}
                  </p>

                  {isSearching ? (
                    <div className="flex items-center gap-2 px-3 py-3 text-sm text-mw-neutral-500">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      {tCampaigns("agency_form.searching")}
                    </div>
                  ) : suggestions.length === 0 ? (
                    <p className="px-3 py-3 text-sm text-mw-neutral-400 dark:text-mw-neutral-500">
                      {tCampaigns("agency_form.no_similar_agencies")}
                    </p>
                  ) : (
                    <ul className="max-h-64 overflow-y-auto divide-y divide-mw-neutral-100 dark:divide-mw-neutral-700">
                      {suggestions.map((agency) => (
                        <li key={agency.id}>
                          <button
                            type="button"
                            className="w-full flex items-center gap-3 px-3 py-2.5 text-left hover:bg-mw-neutral-50 dark:hover:bg-mw-neutral-700 transition-colors cursor-pointer"
                            onClick={() => handleSelectSuggestion(agency)}
                          >
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-mw-neutral-900 dark:text-mw-neutral-100 truncate">
                                {agency.name}
                              </p>
                              {agency.countryName && (
                                <p className="text-xs text-mw-neutral-500 dark:text-mw-neutral-400">
                                  {agency.countryName}
                                </p>
                              )}
                            </div>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>

            {/* Company Email */}
            <Input
              {...register("companyEmail")}
              label={tCampaigns("agency_form.company_email")}
              id="companyEmail"
              placeholder={tCampaigns("agency_form.company_email_placeholder")}
              error={errors.companyEmail?.message}
              type="email"
              required
            />

            {/* Domain */}
            <Input
              {...register("domain")}
              label={tCampaigns("agency_form.domain")}
              id="domain"
              placeholder={tCampaigns("agency_form.domain_placeholder")}
              error={errors.domain?.message}
              type="text"
              required
            />
          </div>
        </form>
      )}
    </ModalDrawer>
  );
};

export default AgencyCreationForm;
