import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { IntercomProvider } from "../IntercomProvider";

const intercomBoot = vi.fn();
const intercomShutdown = vi.fn();
const intercomUpdate = vi.fn();

vi.mock("@intercom/messenger-js-sdk", () => ({
  default: (args: unknown) => intercomBoot(args),
  shutdown: () => intercomShutdown(),
  update: (args: unknown) => intercomUpdate(args),
}));

const fetchJwt = vi.fn();
vi.mock("../intercomApi", () => ({
  useLazyGetIntercomJwtQuery: () => [fetchJwt],
}));

let isAuthenticated = false;
vi.mock("@store", () => ({
  useAppSelector: (selector: (s: unknown) => unknown) =>
    selector({ auth: { isAuthenticated } }),
}));

vi.mock("react-router-dom", () => ({
  useLocation: () => ({ pathname: "/campaigns" }),
}));

describe("IntercomProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    isAuthenticated = false;
  });

  it("boots with a server-signed JWT when authenticated", async () => {
    isAuthenticated = true;
    fetchJwt.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          data: { token: "signed.jwt.token", app_id: "uzi0kcnq" },
        }),
    });

    render(
      <IntercomProvider>
        <div>child</div>
      </IntercomProvider>,
    );

    await waitFor(() =>
      expect(intercomBoot).toHaveBeenCalledWith({
        app_id: "uzi0kcnq",
        intercom_user_jwt: "signed.jwt.token",
        alignment: "left",
      }),
    );
  });

  it("does not boot when not authenticated", async () => {
    isAuthenticated = false;

    render(
      <IntercomProvider>
        <div>child</div>
      </IntercomProvider>,
    );

    await waitFor(() => expect(intercomBoot).not.toHaveBeenCalled());
    expect(fetchJwt).not.toHaveBeenCalled();
  });

  it("does not boot when the JWT fetch fails", async () => {
    isAuthenticated = true;
    fetchJwt.mockReturnValue({
      unwrap: () => Promise.reject(new Error("401")),
    });

    render(
      <IntercomProvider>
        <div>child</div>
      </IntercomProvider>,
    );

    await waitFor(() => expect(fetchJwt).toHaveBeenCalled());
    expect(intercomBoot).not.toHaveBeenCalled();
  });

  it("shuts the Messenger down on unmount", () => {
    const { unmount } = render(
      <IntercomProvider>
        <div>child</div>
      </IntercomProvider>,
    );

    unmount();
    expect(intercomShutdown).toHaveBeenCalled();
  });
});
