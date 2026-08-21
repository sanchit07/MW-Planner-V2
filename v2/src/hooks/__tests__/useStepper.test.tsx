import { configureStore } from "@reduxjs/toolkit";
import stepperSlice, {
  initializeStepper,
  goToStep,
  goToNextStep,
  goToPreviousStep,
  markStepCompleted,
  setStepperLoading,
  updateStepAccessibility,
  completeStepper,
  resetStepper,
  clearStepperFormData,
  setEditMode,
} from "@services/stepper/stepperSlice";
import { renderHook, act } from "@testing-library/react";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, beforeEach, vi } from "vitest";

import { useStepper } from "../useStepper";

const createStore = () =>
  configureStore({
    reducer: { stepper: stepperSlice },
  });

const wrapper = ({ children }: { children: React.ReactNode }) =>
  React.createElement(Provider, { store: createStore(), children });

describe("useStepper", () => {
  beforeEach(() => {});

  it("returns all state and action properties", () => {
    const { result } = renderHook(() => useStepper(), { wrapper });
    expect(result.current.stepperState).toBeDefined();
    expect(result.current.currentStep).toBeUndefined();
    expect(result.current.currentStepId).toBe(1);
    expect(result.current.steps).toEqual([]);
    expect(result.current.isFirstStep).toBe(false);
    expect(result.current.isLastStep).toBe(false);
    expect(result.current.canGoNext).toBe(false);
    expect(result.current.canGoPrevious).toBe(false);
    expect(result.current.progress).toEqual({
      current: 0,
      total: 0,
      percentage: 0,
    });
    expect(result.current.isLoading).toBe(false);
    expect(result.current.isInitialized).toBe(false);
    expect(result.current.isEditMode).toBe(false);
    expect(result.current.editCampaignId).toBeNull();
    expect(typeof result.current.initialize).toBe("function");
    expect(typeof result.current.navigateToStep).toBe("function");
    expect(typeof result.current.nextStep).toBe("function");
    expect(typeof result.current.previousStep).toBe("function");
    expect(typeof result.current.markCompleted).toBe("function");
    expect(typeof result.current.setLoading).toBe("function");
    expect(typeof result.current.updateAccessibility).toBe("function");
    expect(typeof result.current.complete).toBe("function");
    expect(typeof result.current.reset).toBe("function");
    expect(typeof result.current.clearFormData).toBe("function");
    expect(typeof result.current.setStepperEditMode).toBe("function");
  });

  it("initialize dispatches initializeStepper with config", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.initialize({
        steps: [
          { id: 1, title: "Step 1" },
          { id: 2, title: "Step 2" },
        ],
        initialStepId: 1,
      });
    });
    expect(dispatchSpy).toHaveBeenCalledWith(
      initializeStepper({
        steps: [
          { id: 1, title: "Step 1" },
          { id: 2, title: "Step 2" },
        ],
        initialStepId: 1,
      }),
    );
  });

  it("navigateToStep dispatches goToStep", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.navigateToStep(2);
    });
    expect(dispatchSpy).toHaveBeenCalledWith(goToStep(2));
  });

  it("nextStep dispatches goToNextStep", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.nextStep();
    });
    expect(dispatchSpy).toHaveBeenCalledWith(goToNextStep());
  });

  it("previousStep dispatches goToPreviousStep", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.previousStep();
    });
    expect(dispatchSpy).toHaveBeenCalledWith(goToPreviousStep());
  });

  it("markCompleted dispatches markStepCompleted", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.markCompleted(1);
    });
    expect(dispatchSpy).toHaveBeenCalledWith(markStepCompleted(1));
  });

  it("setLoading dispatches setStepperLoading", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.setLoading(true);
    });
    expect(dispatchSpy).toHaveBeenCalledWith(setStepperLoading(true));
  });

  it("updateAccessibility dispatches updateStepAccessibility", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.updateAccessibility();
    });
    expect(dispatchSpy).toHaveBeenCalledWith(updateStepAccessibility());
  });

  it("complete dispatches completeStepper", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.complete();
    });
    expect(dispatchSpy).toHaveBeenCalledWith(completeStepper());
  });

  it("reset dispatches resetStepper", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.reset();
    });
    expect(dispatchSpy).toHaveBeenCalledWith(resetStepper());
  });

  it("clearFormData dispatches clearStepperFormData", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.clearFormData();
    });
    expect(dispatchSpy).toHaveBeenCalledWith(clearStepperFormData());
  });

  it("setStepperEditMode dispatches setEditMode", () => {
    const store = createStore();
    const dispatchSpy = vi.spyOn(store, "dispatch");
    const storeWrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(Provider, { store, children });
    const { result } = renderHook(() => useStepper(), {
      wrapper: storeWrapper,
    });
    act(() => {
      result.current.setStepperEditMode(true, "campaign-123");
    });
    expect(dispatchSpy).toHaveBeenCalledWith(
      setEditMode({ isEditMode: true, campaignId: "campaign-123" }),
    );
  });
});
