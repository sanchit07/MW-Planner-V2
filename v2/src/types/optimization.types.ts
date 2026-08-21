export const PRESET_SCHEDULES: Record<
  string,
  {
    weekdayDistribution: {
      MONDAY: number;
      TUESDAY: number;
      WEDNESDAY: number;
      THURSDAY: number;
      FRIDAY: number;
      SATURDAY: number;
      SUNDAY: number;
    };
    daypartDistribution: {
      "06-10": number;
      "10-14": number;
      "14-18": number;
      "18-22": number;
      "22-06": number;
    };
  }
> = {
  commuter: {
    weekdayDistribution: {
      MONDAY: 20,
      TUESDAY: 20,
      WEDNESDAY: 20,
      THURSDAY: 20,
      FRIDAY: 20,
      SATURDAY: 0,
      SUNDAY: 0,
    },
    daypartDistribution: {
      "06-10": 40,
      "10-14": 10,
      "14-18": 10,
      "18-22": 40,
      "22-06": 0,
    },
  },
  weekend: {
    weekdayDistribution: {
      MONDAY: 5,
      TUESDAY: 5,
      WEDNESDAY: 5,
      THURSDAY: 5,
      FRIDAY: 20,
      SATURDAY: 30,
      SUNDAY: 30,
    },
    daypartDistribution: {
      "06-10": 10,
      "10-14": 30,
      "14-18": 30,
      "18-22": 20,
      "22-06": 10,
    },
  },
  business: {
    weekdayDistribution: {
      MONDAY: 20,
      TUESDAY: 20,
      WEDNESDAY: 20,
      THURSDAY: 20,
      FRIDAY: 20,
      SATURDAY: 0,
      SUNDAY: 0,
    },
    daypartDistribution: {
      "06-10": 25,
      "10-14": 50,
      "14-18": 25,
      "18-22": 0,
      "22-06": 0,
    },
  },
  night: {
    weekdayDistribution: {
      MONDAY: 0,
      TUESDAY: 0,
      WEDNESDAY: 0,
      THURSDAY: 20,
      FRIDAY: 30,
      SATURDAY: 30,
      SUNDAY: 20,
    },
    daypartDistribution: {
      "06-10": 0,
      "10-14": 0,
      "14-18": 20,
      "18-22": 50,
      "22-06": 30,
    },
  },
};

export const PRESETS = {
  BUTTON_ARRAY: [
    {
      value: "commuter",
      label: "optimization.schedulingTargeting.commuterButtonLabel",
    },
    {
      value: "weekend",
      label: "optimization.schedulingTargeting.weekendButtonLabel",
    },
    {
      value: "business",
      label: "optimization.schedulingTargeting.businessButtonLabel",
    },
    {
      value: "night",
      label: "optimization.schedulingTargeting.nightButtonLabel",
    },
    {
      value: "custom",
      label: "optimization.schedulingTargeting.customButtonLabel",
    },
  ],
};
