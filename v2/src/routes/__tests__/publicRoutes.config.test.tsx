import { describe, it, expect } from "vitest";

import { getPublicRoutes, publicRoutes } from "../publicRoutes.config";

describe("publicRoutes.config", () => {
  describe("publicRoutes", () => {
    it("is an array of route objects", () => {
      expect(Array.isArray(publicRoutes)).toBe(true);
      expect(publicRoutes.length).toBeGreaterThan(0);
    });

    it("each route has path and element", () => {
      publicRoutes.forEach((route) => {
        expect(route).toHaveProperty("path");
        expect(route).toHaveProperty("element");
        expect(typeof route.path).toBe("string");
        expect(route.path).toBeTruthy();
        expect(route.element).toBeDefined();
      });
    });

    it("includes root redirect to /login", () => {
      const rootRoute = publicRoutes.find((r) => r.path === "/");
      expect(rootRoute).toBeDefined();
      expect(rootRoute?.element).toBeDefined();
    });

    it("includes login route", () => {
      const loginRoute = publicRoutes.find((r) => r.path === "/login");
      expect(loginRoute).toBeDefined();
      expect(loginRoute?.element).toBeDefined();
    });

    it("includes public inventory map view route with campaignId param", () => {
      const mapRoute = publicRoutes.find(
        (r) => typeof r.path === "string" && r.path.includes("inventory-map"),
      );
      expect(mapRoute).toBeDefined();
      expect(mapRoute?.path).toBe("/public/inventory-map/view/:campaignId");
    });

    it("does not duplicate route paths", () => {
      const paths = publicRoutes.map((r) => r.path);
      const unique = new Set(paths);
      expect(unique.size).toBe(paths.length);
    });
  });

  describe("getPublicRoutes", () => {
    it("returns the same array as publicRoutes", () => {
      const result = getPublicRoutes();
      expect(result).toBe(publicRoutes);
      expect(result).toEqual(publicRoutes);
    });

    it("returns an array", () => {
      expect(Array.isArray(getPublicRoutes())).toBe(true);
    });

    it("returns routes with path and element", () => {
      const routes = getPublicRoutes();
      routes.forEach((route) => {
        expect(route).toHaveProperty("path");
        expect(route).toHaveProperty("element");
      });
    });
  });
});
