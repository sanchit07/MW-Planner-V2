import PageHeader from "@components/PageHeader";
import { Button } from "@components/ui/Button";
import { Dropdown, DropdownTrigger } from "@components/ui/Dropdown";
import { Modal } from "@components/ui/Modal";
import { Loading } from "@components/ui/Spinner";
import { StatusBadge } from "@components/ui/StatusBadge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@components/ui/Tabs";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  useGetExecutionPlanStatusQuery,
  useLazyViewCampaignQuery,
  useSubmitForReviewMutation,
} from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import { ArrowLeft, MoreHorizontal, Info, Rocket } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import CampaignDetails from "./CampaignDetails";
import CampaignHistory from "./CampaignHistory";
import { CampaignActionsDropdownContent } from "../components/CampaignActionsDropdownContent";

const CampaignViewDetailsPage = () => {
  const navigate = useNavigate();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { campaignId: campaignIdFromUrl } = useParams<{ campaignId: string }>();
  const [activeMainTab, setActiveMainTab] = useState("campaign-details");
  const [showSubmitModal, setShowSubmitModal] = useState(false);

  const campaignId = campaignIdFromUrl || "";
  const { showSuccess, showError } = useAnnounce();

  // API hook for generating proposal
  const [submitForReview] = useSubmitForReviewMutation();
  // Lightweight execution/handoff state (only surfaced once the plan was pushed).
  const { data: executionStatusData } = useGetExecutionPlanStatusQuery(
    campaignId,
    { skip: !campaignId },
  );
  const executionStatus = executionStatusData?.data;
  const showExecutionPanel = !!executionStatus?.exists && executionStatus.locked;
  const [
    getViewData,
    {
      data: campaignData,
      isLoading: isViewDataLoading,
      isError: isViewDataError,
    },
  ] = useLazyViewCampaignQuery();

  const handleBack = () => {
    navigate("/campaigns");
  };

  const backButton = (
    <div onClick={handleBack}>
      <ArrowLeft className="w-5 h-5" cursor="pointer" />
    </div>
  );

  const handleSubmitCampaign = () => {
    // Show confirmation modal instead of directly submitting
    setShowSubmitModal(true);
  };

  const handleConfirmSubmit = async () => {
    setShowSubmitModal(false);

    // Call the submit for review API
    const result = await submitForReview(campaignId).unwrap();

    if (result.success) {
      showSuccess(result.data || tCampaigns("viewCampaign.submitSuccess"));
      navigate("/campaigns");
    } else {
      showError(tCampaigns("viewCampaign.submitError"));
    }
  };

  const handleCancelSubmit = () => {
    setShowSubmitModal(false);
  };

  // Create refetch function that refetches all queries
  const refetch = useCallback(() => {
    if (campaignIdFromUrl) {
      getViewData(campaignIdFromUrl);
    }
  }, [campaignIdFromUrl, getViewData]);

  useEffect(() => {
    if (campaignIdFromUrl) {
      getViewData(campaignIdFromUrl);
    }
  }, [campaignIdFromUrl, getViewData]);

  return (
    <>
      {isViewDataLoading && (
        <Loading overlay={true} text="" variant="primary" />
      )}
      {isViewDataError && (
        <div>{tCampaigns("viewCampaign.errorLoadingData")}</div>
      )}
      {campaignData && (
        <div className="h-full flex flex-col">
          <PageHeader
            title={
              campaignData.data?.name ||
              tCampaigns("viewCampaign.defaultCampaignName")
            }
            description={
              <div className="inline-flex items-center gap-2">
                {campaignData.data?.planNumber && (
                  <p>
                    {tCampaigns("viewCampaign.ID")}:{" "}
                    {campaignData.data.planNumber}
                  </p>
                )}

                <StatusBadge
                  status={
                    campaignData.data?.status.toLocaleLowerCase() || "draft"
                  }
                >
                  {campaignData.data?.status
                    ? tCampaigns(
                        `campaignsList.status.${campaignData.data.status}`,
                      ) || campaignData.data.status
                    : "N/A"}
                </StatusBadge>
              </div>
            }
            actions={
              <>
                {(campaignData.data?.status.toLocaleLowerCase() == "planned" ||
                  campaignData.data?.status.toLocaleLowerCase() ==
                    "negotiating") && (
                  <Button size="md" onClick={handleSubmitCampaign}>
                    {tCampaigns("viewCampaign.submitCampaign")}
                  </Button>
                )}
                {["approved", "active", "completed"].includes(
                  campaignData.data?.status.toLocaleLowerCase() || "",
                ) && (
                  <Button
                    size="md"
                    onClick={() =>
                      navigate(`/campaigns/execution-plan/${campaignId}`)
                    }
                  >
                    {tCampaigns("executionPlan.title")}
                  </Button>
                )}
                <Dropdown>
                  <DropdownTrigger asChild>
                    <Button
                      variant="outline"
                      size="iconMd"
                      className="outline-mw-primary-500 text-mw-primary-500"
                    >
                      <MoreHorizontal className="h-4 w-4" />
                    </Button>
                  </DropdownTrigger>
                  <CampaignActionsDropdownContent
                    campaignId={campaignData.data?.id || ""}
                    campaignData={{ status: campaignData.data?.status }}
                    handlers={{
                      onRefresh: refetch,
                    }}
                    hideNavigation={["view"]}
                  />
                </Dropdown>
              </>
            }
            leftAction={backButton}
          />
          {showExecutionPanel && executionStatus && (
            <div className="mx-4 mt-4 px-4 py-3 bg-mw-primary-50 rounded-lg flex flex-wrap items-center gap-3 text-sm">
              <Rocket className="size-4 shrink-0 text-mw-primary-500" />
              <span className="text-mw-primary-500 font-medium">
                {tCampaigns("viewCampaign.execution.pushed")}
                {executionStatus.pushedAt &&
                  ` · ${new Date(executionStatus.pushedAt).toLocaleString()}`}
              </span>
              <span className="text-mw-neutral-700">
                {tCampaigns("viewCampaign.execution.handedOff", {
                  acknowledged: executionStatus.acknowledgedCount,
                  total: executionStatus.lineCount,
                })}
              </span>
              {executionStatus.inProgressCount > 0 && (
                <span className="text-mw-neutral-500">
                  {tCampaigns("viewCampaign.execution.inProgress", {
                    count: executionStatus.inProgressCount,
                  })}
                </span>
              )}
              {executionStatus.failedCount > 0 && (
                <span className="text-mw-error-500 font-medium">
                  {tCampaigns("viewCampaign.execution.failed", {
                    count: executionStatus.failedCount,
                  })}
                </span>
              )}
              <Button
                variant="outline"
                size="sm"
                className="ml-auto"
                onClick={() =>
                  navigate(`/campaigns/execution-plan/${campaignId}`)
                }
              >
                {tCampaigns("viewCampaign.execution.view")}
              </Button>
            </div>
          )}
          <div
            className={`flex-1 scrollbar-thin flex flex-col ${activeMainTab === "campaign-details" ? "overflow-y-auto" : "overflow-hidden"}`}
          >
            <Tabs
              value={activeMainTab}
              onValueChange={setActiveMainTab}
              className="h-full flex flex-col"
            >
              <div className="px-4 pt-4">
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="campaign-details">
                    {tCampaigns("viewCampaign.tabs.campaignDetails")}
                  </TabsTrigger>
                  <TabsTrigger value="campaign-history">
                    {tCampaigns("viewCampaign.tabs.campaignHistory")}
                  </TabsTrigger>
                </TabsList>
              </div>
              <div
                className={`flex-1 ${activeMainTab === "campaign-details" ? "overflow-y-auto" : "overflow-hidden"}`}
              >
                <div className={`p-4 h-full`}>
                  <TabsContent value="campaign-details" className="p-0">
                    {campaignIdFromUrl && (
                      <CampaignDetails campaignId={campaignIdFromUrl} />
                    )}
                  </TabsContent>
                  <TabsContent value="campaign-history" className="p-0 h-full">
                    {campaignIdFromUrl && (
                      <CampaignHistory campaignId={campaignIdFromUrl} />
                    )}
                  </TabsContent>
                </div>
              </div>
            </Tabs>
          </div>
        </div>
      )}
      {/* Submit Confirmation Modal */}
      <Modal
        isOpen={showSubmitModal}
        onClose={handleCancelSubmit}
        title={tCampaigns("viewCampaign.submitModal.title")}
        primaryButtonText={tCampaigns("viewCampaign.submitModal.yes")}
        secondaryButtonText={tCampaigns("viewCampaign.submitModal.no")}
        onPrimaryAction={handleConfirmSubmit}
        onSecondaryAction={handleCancelSubmit}
        size="md"
      >
        <div className="space-y-4">
          <p className="text-sm text-mw-neutral-600 dark:text-mw-neutral-300">
            {tCampaigns("viewCampaign.submitModal.message")}
          </p>

          {/* Notes Section */}
          <div className="self-stretch px-4 py-2 bg-mw-error-50 rounded inline-flex justify-start items-start gap-8">
            <div className="flex-1 flex justify-start items-start gap-2">
              <div className="w-4 h-4 relative overflow-hidden shrink-0 mt-0.5">
                <Info className="w-4 h-4 text-mw-error-500" />
              </div>
              <div className="flex-1 flex justify-start items-center gap-4 flex-wrap content-center">
                <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
                  <div className="justify-start text-mw-error-500 text-sm font-medium font-['Poppins'] leading-4">
                    {tCampaigns("approval.modal.note")}
                  </div>
                  <div className="self-stretch justify-start text-mw-error-500 text-sm font-normal font-['Poppins'] leading-4">
                    {tCampaigns("viewCampaign.submitModal.note")}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Modal>
    </>
  );
};

export default CampaignViewDetailsPage;
