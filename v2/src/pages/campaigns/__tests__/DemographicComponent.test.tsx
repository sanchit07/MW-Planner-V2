import { configureStore } from "@reduxjs/toolkit";
import { render, screen } from "@testing-library/react";
import React from "react";
import { FormProvider, useForm } from "react-hook-form";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { TargetingFormData } from "../../../schemas/campaigns/targeting.schema";
import DemographicComponent from "../DemographicComponent";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
}));

vi.mock(
  "../../../services/configuration-metadata/configurationMetadataSlice",
  async (importOriginal) => {
    const actual =
      await importOriginal<
        typeof import("../../../services/configuration-metadata/configurationMetadataSlice")
      >();
    return {
      ...actual,
      useConfigurationMetadataQuery: () => ({
        data: null,
        error: null,
        isLoading: false,
        refetch: vi.fn(),
      }),
      setConfigurationMetaData: vi.fn(),
    };
  },
);

const defaultDemographics = {
  age: [] as string[],
  gender: [] as string[],
  income: [] as string[],
  interests: [] as string[],
  venues: [] as string[],
  behavior: [] as string[],
};

const testStore = configureStore({
  reducer: {
    configurationMetadata: () => ({
      demographics: {
        age: [],
        gender: [],
        income: [],
        interests: [],
        venues: [],
        behavior: [],
      },
    }),
  },
});

function TestWrapperInner(
  props: Partial<React.ComponentProps<typeof DemographicComponent>> = {},
) {
  const methods = useForm<TargetingFormData>({
    defaultValues: {
      demographics: defaultDemographics,
      geofencing: { geometries: [], locations: [] },
      signals: [],
    },
  });
  const onFieldChange = vi.fn().mockResolvedValue(undefined);
  return (
    <FormProvider {...methods}>
      <DemographicComponent
        control={methods.control}
        onFieldChange={onFieldChange}
        demographicFormData={methods.getValues("demographics")}
        {...props}
      />
    </FormProvider>
  );
}

function TestWrapper(
  props: Partial<React.ComponentProps<typeof DemographicComponent>> = {},
) {
  return (
    <Provider store={testStore}>
      <TestWrapperInner {...props} />
    </Provider>
  );
}

function renderDemographicComponent(
  props: Partial<React.ComponentProps<typeof DemographicComponent>> = {},
) {
  return render(<TestWrapper {...props} />);
}

describe("DemographicComponent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Feedback SI 46: income group options must show text only —
  // no numeric range values (previously rendered as option descriptions).
  describe("income group options", () => {
    const storeWithIncomeMetadata = configureStore({
      reducer: {
        configurationMetadata: () => ({
          demographics: {
            age: [],
            gender: [],
            income: [
              { demoKey: "low", name: "Low income", description: "<30,000" },
              {
                demoKey: "middle",
                name: "Middle income",
                description: "50,000–100,000",
              },
            ],
            interests: [],
            venues: [],
            behavior: [],
          },
        }),
      },
    });

    it("renders income labels without the numeric range values", async () => {
      const user = (
        await import("@testing-library/user-event")
      ).default.setup();
      render(
        <Provider store={storeWithIncomeMetadata}>
          <TestWrapperInner />
        </Provider>,
      );

      await user.click(screen.getByText("targeting.incomeGroup.placeholder"));

      expect(await screen.findByText("Low income")).toBeInTheDocument();
      expect(screen.getByText("Middle income")).toBeInTheDocument();
      expect(screen.queryByText("<30,000")).not.toBeInTheDocument();
      expect(screen.queryByText("50,000–100,000")).not.toBeInTheDocument();
    });
  });

  // Gender "other" option is intentionally excluded from both fallback and API.
  describe("gender options", () => {
    it("shows male and female but not other in the fallback list", async () => {
      const user = (
        await import("@testing-library/user-event")
      ).default.setup();
      renderDemographicComponent();

      await user.click(screen.getByText("targeting.gender.placeholder"));

      expect(
        await screen.findByText("targeting.fallback.gender.male"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("targeting.fallback.gender.female"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("targeting.fallback.gender.other"),
      ).not.toBeInTheDocument();
    });

    it("filters out an 'other' option coming from the API", async () => {
      const user = (
        await import("@testing-library/user-event")
      ).default.setup();
      const storeWithGenderMetadata = configureStore({
        reducer: {
          configurationMetadata: () => ({
            demographics: {
              age: [],
              gender: [
                { demoKey: "male", name: "Male", description: "" },
                { demoKey: "female", name: "Female", description: "" },
                { demoKey: "other", name: "Other", description: "" },
              ],
              income: [],
              interests: [],
              venues: [],
              behavior: [],
            },
          }),
        },
      });
      render(
        <Provider store={storeWithGenderMetadata}>
          <TestWrapperInner />
        </Provider>,
      );

      await user.click(screen.getByText("targeting.gender.placeholder"));

      expect(await screen.findByText("Male")).toBeInTheDocument();
      expect(screen.getByText("Female")).toBeInTheDocument();
      expect(screen.queryByText("Other")).not.toBeInTheDocument();
    });
  });

  describe("rendering", () => {
    it("renders demography selection title and description", () => {
      renderDemographicComponent();
      expect(
        screen.getByText("targeting.demography_selection.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("targeting.demography_selection.description"),
      ).toBeInTheDocument();
    });

    it("renders age, gender, income, interests, and behavior sections", () => {
      renderDemographicComponent();
      expect(screen.getByText("targeting.age.title")).toBeInTheDocument();
      expect(screen.getByText("targeting.gender.title")).toBeInTheDocument();
      expect(
        screen.getByText("targeting.incomeGroup.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("targeting.interestActivity.title"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("targeting.audienceBehavior.title"),
      ).toBeInTheDocument();
    });

    it("does not render venue type section (moved to Inventory Types tab)", () => {
      renderDemographicComponent();
      expect(
        screen.queryByText("targeting.venueType.title"),
      ).not.toBeInTheDocument();
    });
  });
});
