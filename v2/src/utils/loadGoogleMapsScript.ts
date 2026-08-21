const GOOGLE_MAPS_SCRIPT_SELECTOR = 'script[src*="maps.googleapis.com"]';

export function loadGoogleMapsScript(apiKey: string): void {
  const existingScript = document.querySelector(GOOGLE_MAPS_SCRIPT_SELECTOR);

  if (existingScript) {
    return;
  }

  const script = document.createElement("script");
  script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&libraries=places&loading=async`;
  script.async = true;
  script.defer = true;
  document.body.appendChild(script);
}
