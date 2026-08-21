export const COUNTRY_CENTRE_MAPPINGS: Record<
  string,
  { center: [number, number]; zoom: number }
> = {
  Malaysia: { center: [103.963586, 1.3352566], zoom: 12 }, // Malaysia's capital (Kuala Lumpur)
  Brazil: { center: [-47.9292, -15.7801], zoom: 12 }, // Brazil's capital (Brasília)
  // Add more countries and capitals as needed
  US: { center: [-77.0369, 38.9072], zoom: 12 }, // Washington, D.C.
  UK: { center: [-0.1278, 51.5074], zoom: 12 }, // London
  Japan: { center: [139.6917, 35.6895], zoom: 12 }, // Tokyo
  Singapore: { center: [103.8198, 1.3521], zoom: 12 }, // Singapore
  AntiguaAndBarbuda: { center: [-61.8468, 17.1274], zoom: 12 }, // St. John's (Antigua)
  // Add more countries here...
};
