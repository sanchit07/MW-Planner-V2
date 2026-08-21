import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, it, expect } from "vitest";

// Feedback SI 52: toast/notification banners rendered in react-toastify's
// default sans-serif (arial/helvetica look) instead of the platform font.
// The override must pin --toastify-font-family to Poppins.
describe("toast notification font", () => {
  it("overrides react-toastify font family to the platform font", () => {
    const sass = readFileSync(resolve(__dirname, "../global.sass"), "utf-8");
    expect(sass).toMatch(/--toastify-font-family:\s*"Poppins", sans-serif/);
  });
});
