import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import { Textarea, type MentionOption } from "../Textarea";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

const defaultMentionOptions: MentionOption[] = [
  { id: "1", label: "John", value: "john" },
  { id: "2", label: "Jane", value: "jane" },
  { id: "3", label: "Bob", value: "bob" },
];

describe("Textarea", () => {
  beforeEach(() => {
    vi.spyOn(window, "getComputedStyle").mockReturnValue({
      lineHeight: "24px",
      paddingLeft: "12px",
    } as CSSStyleDeclaration);
    vi.spyOn(
      HTMLTextAreaElement.prototype,
      "getBoundingClientRect",
    ).mockReturnValue({
      top: 0,
      left: 0,
      width: 400,
      height: 200,
      bottom: 200,
      right: 400,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    });
  });

  it("should render textarea", () => {
    render(<Textarea />);
    const textarea = screen.getByRole("textbox");
    expect(textarea).toBeInTheDocument();
  });

  it("should render with label", () => {
    render(<Textarea label="Description" />);
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
  });

  it("should pass required to label when required prop is set", () => {
    render(<Textarea label="Description" required />);
    const label = document.querySelector('label[for="textarea-description"]');
    expect(label).toBeInTheDocument();
  });

  it("should generate id from label when no id and no name", () => {
    render(<Textarea label="My Field" />);
    const textarea = screen.getByRole("textbox");
    expect(textarea.id).toBe("textarea-my-field");
  });

  it("should generate id with label spaces replaced by hyphens", () => {
    render(<Textarea label="Test Label Here" />);
    const textarea = screen.getByRole("textbox");
    expect(textarea.id).toBe("textarea-test-label-here");
  });

  it("should use fallback id when no id, name, or label", () => {
    render(<Textarea />);
    const textarea = screen.getByRole("textbox");
    expect(textarea.id).toMatch(/^textarea-field$/);
  });

  it("should display error message", () => {
    render(<Textarea error="This field is required" />);
    expect(screen.getByText("This field is required")).toBeInTheDocument();
  });

  it("should apply error styling to textarea when error prop is set", () => {
    const { container } = render(<Textarea error="Error" />);
    const textarea = container.querySelector("textarea");
    expect(textarea?.className).toContain("border-mw-error");
  });

  it("should display help text when no error", () => {
    render(<Textarea helpText="Enter a description" />);
    expect(screen.getByText("Enter a description")).toBeInTheDocument();
  });

  it("should not display help text when error is present", () => {
    render(
      <Textarea
        helpText="Enter a description"
        error="This field is required"
      />,
    );
    expect(screen.getByText("This field is required")).toBeInTheDocument();
    expect(screen.queryByText("Enter a description")).not.toBeInTheDocument();
  });

  it("should associate error message with textarea via id", () => {
    render(<Textarea id="desc" error="Required" />);
    const error = document.getElementById("desc-error");
    expect(error).toHaveTextContent("Required");
  });

  it("should associate help text with textarea via id when no error", () => {
    render(<Textarea id="desc" helpText="Hint" />);
    const help = document.getElementById("desc-help");
    expect(help).toHaveTextContent("Hint");
  });

  it("should handle input change", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn();
    render(<Textarea onChange={handleChange} />);

    const textarea = screen.getByRole("textbox");
    await user.type(textarea, "test");

    expect(handleChange).toHaveBeenCalled();
  });

  it("should update internal value when typing without onChange", async () => {
    const user = userEvent.setup();
    render(<Textarea />);
    const textarea = screen.getByRole("textbox");
    await user.type(textarea, "hello");
    expect(textarea).toHaveValue("hello");
  });

  it("should show character count when enabled", () => {
    render(<Textarea showCharCount maxLength={100} />);
    expect(screen.getByText(/\/100/)).toBeInTheDocument();
  });

  it("should use currentLength when provided", () => {
    render(<Textarea showCharCount maxLength={100} currentLength={50} />);
    expect(screen.getByText("50/100")).toBeInTheDocument();
  });

  it("should use internal value length for char count when currentLength not provided", async () => {
    const user = userEvent.setup();
    render(<Textarea showCharCount maxLength={100} />);
    const textarea = screen.getByRole("textbox");
    await user.type(textarea, "hi");
    expect(screen.getByText("2/100")).toBeInTheDocument();
  });

  it("should add pb-6 class when showCharCount is true", () => {
    const { container } = render(<Textarea showCharCount maxLength={100} />);
    const textarea = container.querySelector("textarea");
    expect(textarea?.className).toContain("pb-6");
  });

  it("should be disabled when disabled prop is set", () => {
    render(<Textarea disabled />);
    const textarea = screen.getByRole("textbox");
    expect(textarea).toBeDisabled();
  });

  it("should forward ref", () => {
    const ref = React.createRef<HTMLTextAreaElement>();
    render(<Textarea ref={ref} />);
    expect(ref.current).toBeInstanceOf(HTMLTextAreaElement);
  });

  it("should call function ref with textarea element", () => {
    const refFn = vi.fn();
    render(<Textarea ref={refFn} />);
    expect(refFn).toHaveBeenCalledWith(expect.any(HTMLTextAreaElement));
  });

  it("should generate id from name when provided", () => {
    render(<Textarea name="description" />);
    const textarea = screen.getByRole("textbox");
    expect(textarea.id).toBe("textarea-description");
  });

  it("should use provided id", () => {
    render(<Textarea id="custom-id" />);
    const textarea = screen.getByRole("textbox");
    expect(textarea.id).toBe("custom-id");
  });

  it("should apply custom className", () => {
    const { container } = render(<Textarea className="custom-class" />);
    const textarea = container.querySelector("textarea");
    expect(textarea?.className).toContain("custom-class");
  });

  it("should sync internal value when controlled value prop changes", () => {
    const { rerender } = render(
      <Textarea value="initial" onChange={() => {}} />,
    );
    const textarea = screen.getByRole("textbox");
    expect(textarea).toHaveValue("initial");
    rerender(<Textarea value="updated" onChange={() => {}} />);
    expect(textarea).toHaveValue("updated");
  });

  it("should use controlled value when value prop is provided", () => {
    render(<Textarea value="controlled" onChange={() => {}} />);
    expect(screen.getByRole("textbox")).toHaveValue("controlled");
  });

  it("should pass through other textarea props", () => {
    render(
      <Textarea placeholder="Enter text" rows={5} data-testid="my-textarea" />,
    );
    const textarea = screen.getByRole("textbox");
    expect(textarea).toHaveAttribute("placeholder", "Enter text");
    expect(textarea).toHaveAttribute("rows", "5");
  });

  describe("mentions", () => {
    it("should show mention dropdown when typing mention key at start", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      expect(screen.getByText("John")).toBeInTheDocument();
      expect(screen.getByText("Jane")).toBeInTheDocument();
      expect(screen.getByText("Bob")).toBeInTheDocument();
    });

    it("should show mention dropdown when typing mention key after space", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "hello @");
      expect(screen.getByText("John")).toBeInTheDocument();
    });

    it("should filter mention options by query", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@j");
      expect(screen.getByText("John")).toBeInTheDocument();
      expect(screen.getByText("Jane")).toBeInTheDocument();
      expect(screen.queryByText("Bob")).not.toBeInTheDocument();
    });

    it("should close dropdown when space is typed after mention key", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@ ");
      expect(screen.queryByText("John")).not.toBeInTheDocument();
    });

    it("should insert mention and call onMentionSelect when option is clicked", async () => {
      const user = userEvent.setup();
      const onMentionSelect = vi.fn();
      const onChange = vi.fn();
      render(
        <Textarea
          enableMentions
          mentionOptions={defaultMentionOptions}
          onMentionSelect={onMentionSelect}
          onChange={onChange}
        />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      const option = screen.getByText("Jane");
      await user.click(option);
      expect(textarea).toHaveValue("@Jane ");
      expect(onMentionSelect).toHaveBeenCalledWith(
        expect.objectContaining({ id: "2", label: "Jane", value: "jane" }),
      );
      expect(onChange).toHaveBeenCalled();
    });

    it("should insert mention and call onChange when option is clicked", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(
        <Textarea
          enableMentions
          mentionOptions={defaultMentionOptions}
          onChange={onChange}
        />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      await user.click(screen.getByText("Bob"));
      expect(textarea).toHaveValue("@Bob ");
      expect(onChange).toHaveBeenLastCalledWith(
        expect.objectContaining({
          target: expect.objectContaining({ value: "@Bob " }),
        }),
      );
    });

    it("should support custom mentionKey", async () => {
      const user = userEvent.setup();
      render(
        <Textarea
          enableMentions
          mentionKey="#"
          mentionOptions={defaultMentionOptions}
        />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "#");
      expect(screen.getByText("John")).toBeInTheDocument();
    });

    it("should close dropdown on Escape", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      expect(screen.getByText("John")).toBeInTheDocument();
      await user.keyboard("{Escape}");
      expect(screen.queryByText("John")).not.toBeInTheDocument();
    });

    it("should select mention on Enter when dropdown is open", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      await user.keyboard("{Enter}");
      expect(textarea).toHaveValue("@John ");
    });

    it("should select mention on Tab when dropdown is open", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      await user.keyboard("{Tab}");
      expect(textarea).toHaveValue("@John ");
    });

    it("should cycle selected index with ArrowDown", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{Enter}");
      expect(textarea).toHaveValue("@Bob ");
    });

    it("should cycle selected index with ArrowUp", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{ArrowDown}");
      await user.keyboard("{ArrowUp}");
      await user.keyboard("{Enter}");
      expect(textarea).toHaveValue("@Jane ");
    });

    it("should not open dropdown when character before mention key is not space or newline", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "a@");
      expect(screen.queryByText("John")).not.toBeInTheDocument();
    });

    it("should call props.onKeyDown when mentions disabled", async () => {
      const user = userEvent.setup();
      const onKeyDown = vi.fn();
      render(<Textarea onKeyDown={onKeyDown} />);
      const textarea = screen.getByRole("textbox");
      await user.click(textarea);
      await user.keyboard("a");
      expect(onKeyDown).toHaveBeenCalled();
    });

    it("should invoke props.onKeyDown when mentions enabled but dropdown closed", async () => {
      const onKeyDown = vi.fn();
      render(
        <Textarea
          enableMentions
          mentionOptions={defaultMentionOptions}
          onKeyDown={onKeyDown}
        />,
      );
      const textarea = screen.getByRole("textbox");
      textarea.focus();
      textarea.dispatchEvent(
        new KeyboardEvent("keydown", { key: "k", bubbles: true }),
      );
      expect(onKeyDown).toHaveBeenCalledTimes(1);
    });

    it("should call props.onKeyDown when dropdown not open and key is not mention shortcut", async () => {
      const user = userEvent.setup();
      const onKeyDown = vi.fn();
      render(
        <Textarea
          enableMentions
          mentionOptions={defaultMentionOptions}
          onKeyDown={onKeyDown}
        />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "x");
      expect(onKeyDown).toHaveBeenCalled();
    });

    it("should call props.onKeyDown when dropdown is open and key is not Arrow/Enter/Tab/Escape", async () => {
      const user = userEvent.setup();
      const onKeyDown = vi.fn();
      render(
        <Textarea
          enableMentions
          mentionOptions={defaultMentionOptions}
          onKeyDown={onKeyDown}
        />,
      );
      const textarea = screen.getByRole("textbox");
      await user.click(textarea);
      await user.keyboard("@");
      expect(screen.getByText("John")).toBeInTheDocument();
      const keydownEvent = new KeyboardEvent("keydown", {
        key: "b",
        bubbles: true,
      });
      textarea.dispatchEvent(keydownEvent);
      expect(onKeyDown).toHaveBeenCalledWith(
        expect.objectContaining({ key: "b" }),
      );
    });

    it("should not call handleMentionSelect when Enter pressed and no matching option", async () => {
      const user = userEvent.setup();
      render(
        <Textarea enableMentions mentionOptions={defaultMentionOptions} />,
      );
      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@z");
      await user.keyboard("{Enter}");
      expect(textarea).toHaveValue("@z");
    });
  });
});
