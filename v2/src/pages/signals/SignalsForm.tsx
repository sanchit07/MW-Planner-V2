import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Label } from "@components/ui/Label";
import MultiSelect from "@components/ui/MultiSelect";
import { TargetingFormData } from "@schemas/campaigns/targeting.schema";
import { useTranslate } from "@tolgee/react";
import { Earth, Info, Plus } from "lucide-react";
import React from "react";
import { Control, Controller } from "react-hook-form";

interface SignalsFormProps {
  control: Control<TargetingFormData>;
  onFieldChange: (value: { signals: string[] }) => Promise<void>;
}

const SignalsForm: React.FC<SignalsFormProps> = ({
  control,
  onFieldChange,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);
  return (
    <div className="flex p-2 pt-4 gap-6">
      <Card className="rounded-xs flex-1">
        <CardHeader className="p-4">
          <div className="flex items-center gap-2 border-b border-mw-neutral-100 pb-2">
            <div className="w-10 h-10 bg-green-50 rounded-lg flex items-center justify-center">
              <Earth className="w-5 h-5 text-green-600" />
            </div>
            <div className="flex-1 inline-flex flex-col justify-start items-start gap-1">
              <div className="self-stretch inline-flex justify-start items-start gap-2">
                <CardTitle className="text-sm font-medium leading-none">
                  {tCampaigns("signalsForm.title")}
                </CardTitle>
              </div>
              <p className="text-sm font-normal text-mw-neutral-500 leading-4">
                {tCampaigns("signalsForm.description")}
              </p>
            </div>
            <Button
              variant="outline"
              size="md"
              onClick={() => {
                // Button click handler - does nothing for now
              }}
              className="ml-auto outline-mw-primary-500 text-mw-primary-500"
            >
              <Plus className="w-4 h-4 mr-2" />
              {tCampaigns("signalsForm.newSignalButton")}
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="mb-2 max-w-md">
            <Label className="text-sm font-medium text-neutral-700 mb-2 block">
              {tCampaigns("signalsForm.label")}
            </Label>

            <Controller
              name="signals"
              control={control}
              render={({ field }) => (
                <MultiSelect
                  options={[
                    {
                      label: tCampaigns(
                        "signalsForm.options.weatherTrigger.label",
                      ),
                      value: "weatherTrigger",
                      description: tCampaigns(
                        "signalsForm.options.weatherTrigger.description",
                      ),
                    },
                    {
                      label: tCampaigns(
                        "signalsForm.options.searchBehaviour.label",
                      ),
                      value: "searchBehaviour",
                      description: tCampaigns(
                        "signalsForm.options.searchBehaviour.description",
                      ),
                    },
                    {
                      label: tCampaigns(
                        "signalsForm.options.footfallPattern.label",
                      ),
                      value: "footfallPattern",
                      description: tCampaigns(
                        "signalsForm.options.footfallPattern.description",
                      ),
                    },
                    {
                      label: tCampaigns("signalsForm.options.timeBased.label"),
                      value: "timeBased",
                      description: tCampaigns(
                        "signalsForm.options.timeBased.description",
                      ),
                    },
                  ]}
                  value={field.value}
                  onChange={(value) => {
                    field.onChange(value);
                  }}
                  onBlur={(value) => {
                    onFieldChange({
                      signals: value,
                    });
                  }}
                  placeholder={tCampaigns("signalsForm.placeholder")}
                  maxVisibleChips={2}
                />
              )}
            />
          </div>
        </CardContent>
      </Card>
      <div className="w-80 space-y-4">
        <Card>
          <CardHeader className="p-4">
            <div className="inline-flex justify-start items-center gap-2">
              <Info className="w-4 h-4 relative overflow-hidden" />
              <h3 className="font-medium text-sm">
                {tCommon("quickTips.title")}
              </h3>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-4 text-xs">
              <div className="flex justify-start items-start gap-2">
                <div className="h-5 flex justify-start items-center gap-2.5">
                  <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                </div>
                <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                  {tCommon("quickTips.brandRecommendations")}
                </div>
              </div>
              <div className="flex justify-start items-start gap-2">
                <div className="h-5 flex justify-start items-center gap-2.5">
                  <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                </div>
                <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                  {tCommon("quickTips.budgetNextStep")}
                </div>
              </div>
              <div className="flex justify-start items-start gap-2">
                <div className="h-5 flex justify-start items-center gap-2.5">
                  <div className="w-3 h-3 bg-mw-primary-500 rounded-full mt-1.5"></div>
                </div>
                <div className="justify-start text-mw-neutral-500 text-xs font-normal leading-tight">
                  {tCommon("quickTips.changeDetailsLater")}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default SignalsForm;
