import type { TargetingFormData } from "@schemas/campaigns/targeting.schema";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { FormProvider, useForm } from "react-hook-form";
import { describe, it, expect, vi, beforeEach } from "vitest";

import SignalsForm from "../SignalsForm";

const mockOnFieldChange = vi.fn().mockResolvedValue(undefined);

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

vi.mock("@components/ui/MultiSelect", () => ({
  default: ({
    value = [],
    onChange,
    onBlur,
    placeholder,
  }: {
    value?: string[];
    onChange?: (v: string[]) => void;
    onBlur?: (v: string[]) => void;
    placeholder?: string;
  }) => (
    <div data-testid="multi-select">
      <input
        data-testid="multi-select-input"
        placeholder={placeholder}
        value={Array.isArray(value) ? value.join(", ") : ""}
        onChange={(e) => {
          const next = e.target.value ? e.target.value.split(", ") : [];
          onChange?.(next);
        }}
        onBlur={() => onBlur?.(value ?? [])}
      />
    </div>
  ),
}));

const defaultValues: TargetingFormData = {
  demographics: {
    age: [],
    gender: [],
    income: [],
    interests: [],
    behavior: [],
  },
  geofencing: {
    geometries: [],
    locations: [],
  },
  signals: [],
  venueTypes: { digitalOoh: [], classicOoh: [] },
  inventoryCluster: [],
  programmaticOnly: false,
};

function SignalsFormWrapper(
  props: Partial<React.ComponentProps<typeof SignalsForm>> = {},
) {
  const methods = useForm<TargetingFormData>({ defaultValues });
  return (
    <FormProvider {...methods}>
      <SignalsForm
        control={methods.control}
        onFieldChange={props.onFieldChange ?? mockOnFieldChange}
        {...props}
      />
    </FormProvider>
  );
}

function renderSignalsForm(
  props: Partial<React.ComponentProps<typeof SignalsForm>> = {},
) {
  return render(<SignalsFormWrapper {...props} />);
}

describe("SignalsForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("rendering", () => {
    it("renders Signals & Triggers card title", () => {
      renderSignalsForm();
      const titles = screen.getAllByText("signalsForm.title");
      expect(titles.length).toBeGreaterThanOrEqual(1);
      expect(titles[0]).toBeInTheDocument();
    });

    it("renders description about triggering advertisements", () => {
      renderSignalsForm();
      expect(screen.getByText("signalsForm.description")).toBeInTheDocument();
    });

    it("renders New Signal button", () => {
      renderSignalsForm();
      expect(
        screen.getByRole("button", { name: /signalsForm.newSignalButton/i }),
      ).toBeInTheDocument();
    });

    it("renders MultiSelect with Signals & Triggers placeholder", () => {
      renderSignalsForm();
      expect(screen.getByTestId("multi-select")).toBeInTheDocument();
      expect(
        screen.getByPlaceholderText("signalsForm.placeholder"),
      ).toBeInTheDocument();
    });

    it("renders Quick Tips section", () => {
      renderSignalsForm();
      expect(screen.getByText("quickTips.title")).toBeInTheDocument();
    });

    it("renders all three quick tip bullets", () => {
      renderSignalsForm();
      expect(
        screen.getByText("quickTips.brandRecommendations"),
      ).toBeInTheDocument();
      expect(screen.getByText("quickTips.budgetNextStep")).toBeInTheDocument();
      expect(
        screen.getByText("quickTips.changeDetailsLater"),
      ).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("New Signal button is clickable and does not throw", async () => {
      const user = userEvent.setup();
      renderSignalsForm();
      const button = screen.getByRole("button", {
        name: /signalsForm.newSignalButton/i,
      });
      await user.click(button);
      expect(button).toBeInTheDocument();
    });

    it("calls onFieldChange with signals array when MultiSelect blurs", async () => {
      const user = userEvent.setup();
      const onFieldChange = vi.fn().mockResolvedValue(undefined);
      renderSignalsForm({ onFieldChange });
      const input = screen.getByTestId("multi-select-input");
      await user.type(input, "weatherTrigger");
      await user.tab();
      expect(onFieldChange).toHaveBeenCalledWith({
        signals: ["weatherTrigger"],
      });
    });

    it("calls onFieldChange with empty array when MultiSelect blurs with no value", async () => {
      const user = userEvent.setup();
      const onFieldChange = vi.fn().mockResolvedValue(undefined);
      renderSignalsForm({ onFieldChange });
      const input = screen.getByTestId("multi-select-input");
      await user.click(input);
      await user.tab();
      expect(onFieldChange).toHaveBeenCalledWith({ signals: [] });
    });
  });

  describe("form integration", () => {
    it("accepts control and onFieldChange props without error", () => {
      expect(() => renderSignalsForm()).not.toThrow();
    });
  });
});
