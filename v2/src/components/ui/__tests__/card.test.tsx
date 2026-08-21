import { render, screen } from "@testing-library/react";
import React from "react";
import { describe, it, expect } from "vitest";

import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from "../card";

describe("Card Components", () => {
  describe("Card", () => {
    it("should render card with children", () => {
      render(
        <Card>
          <div>Card Content</div>
        </Card>,
      );
      expect(screen.getByText("Card Content")).toBeInTheDocument();
    });

    it("should forward ref", () => {
      const ref = React.createRef<HTMLDivElement>();
      render(<Card ref={ref}>Content</Card>);
      expect(ref.current).toBeInstanceOf(HTMLDivElement);
    });

    it("should apply custom className", () => {
      const { container } = render(
        <Card className="custom-class">Content</Card>,
      );
      expect(container.querySelector(".custom-class")).toBeInTheDocument();
    });
  });

  describe("CardHeader", () => {
    it("should render card header", () => {
      render(
        <Card>
          <CardHeader>Header Content</CardHeader>
        </Card>,
      );
      expect(screen.getByText("Header Content")).toBeInTheDocument();
    });

    it("should forward ref", () => {
      const ref = React.createRef<HTMLDivElement>();
      render(
        <Card>
          <CardHeader ref={ref}>Header</CardHeader>
        </Card>,
      );
      expect(ref.current).toBeInstanceOf(HTMLDivElement);
    });
  });

  describe("CardTitle", () => {
    it("should render card title", () => {
      render(
        <Card>
          <CardTitle>Title</CardTitle>
        </Card>,
      );
      expect(screen.getByText("Title")).toBeInTheDocument();
    });
  });

  describe("CardDescription", () => {
    it("should render card description", () => {
      render(
        <Card>
          <CardDescription>Description</CardDescription>
        </Card>,
      );
      expect(screen.getByText("Description")).toBeInTheDocument();
    });
  });

  describe("CardContent", () => {
    it("should render card content", () => {
      render(
        <Card>
          <CardContent>Content</CardContent>
        </Card>,
      );
      expect(screen.getByText("Content")).toBeInTheDocument();
    });

    it("should apply padding classes", () => {
      const { container } = render(
        <Card>
          <CardContent>Content</CardContent>
        </Card>,
      );
      expect(container.querySelector(".p-4")).toBeInTheDocument();
    });
  });

  describe("CardFooter", () => {
    it("should render card footer", () => {
      render(
        <Card>
          <CardFooter>Footer</CardFooter>
        </Card>,
      );
      expect(screen.getByText("Footer")).toBeInTheDocument();
    });
  });
});
