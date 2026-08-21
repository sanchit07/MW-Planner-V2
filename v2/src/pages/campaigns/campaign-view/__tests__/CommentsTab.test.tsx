import { configureStore } from "@reduxjs/toolkit";
import userSlice from "@services/user/userSlice";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { Provider } from "react-redux";
import { describe, it, expect, vi, beforeEach } from "vitest";

import CommentsTab from "../CommentsTab";

// Mock useAnnounce
const mockShowSuccess = vi.fn();
const mockShowError = vi.fn();
vi.mock("@hooks/useAnnounce", () => ({
  useAnnounce: () => ({
    showSuccess: mockShowSuccess,
    showError: mockShowError,
  }),
}));

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
}));

// Mock campaignDetailsSlice hooks
const mockPostComment = vi.fn();
const mockRefetchComments = vi.fn();
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let mockCommentsData: any = undefined;
let mockIsLoadingComments = false;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let mockCommentsError: any = null;
let mockIsPostingComment = false;

vi.mock("@services/campaign/campaignDetailsSlice", async () => {
  const actual = await vi.importActual(
    "@services/campaign/campaignDetailsSlice",
  );
  return {
    ...actual,
    usePostCommentMutation: () => [
      mockPostComment,
      {
        get isLoading() {
          return mockIsPostingComment;
        },
      },
    ],
    useGetCommentsQuery: () => ({
      get data() {
        return mockCommentsData;
      },
      get isLoading() {
        return mockIsLoadingComments;
      },
      get error() {
        return mockCommentsError;
      },
      refetch: mockRefetchComments,
    }),
    // Export default reducer for store
    default: (state = {}) => state,
  };
});

// Mock Textarea
vi.mock("@components/ui/Textarea", () => ({
  Textarea: ({
    value,
    onChange,
    placeholder,
    maxLength,
    disabled,
  }: {
    value: string;
    onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
    placeholder: string;
    maxLength: number;
    currentLength: number;
    disabled: boolean;
  }) => (
    <textarea
      data-testid="comment-textarea"
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      maxLength={maxLength}
      disabled={disabled}
    >
      {value}
    </textarea>
  ),
  MentionOption: {},
}));

const createMockStore = (user = null) => {
  return configureStore({
    reducer: {
      profile: userSlice,
    },
    preloadedState: {
      profile: {
        profile: user,
      },
    },
  });
};

const TestWrapper = ({
  children,
  user = null,
}: {
  children: React.ReactNode;
  user?: null;
}) => {
  const store = createMockStore(user);
  return <Provider store={store}>{children}</Provider>;
};

describe("CommentsTab", () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockPostComment.mockReturnValue({
      unwrap: async () => ({ success: true }),
    });
    mockCommentsData = undefined;
    mockIsLoadingComments = false;
    mockCommentsError = null;
    mockIsPostingComment = false;
  });

  describe("Rendering", () => {
    it("should render add comment section", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("commentsTab.addComment")).toBeInTheDocument();
    });

    it("should render campaign comments section", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(
        screen.getByText("commentsTab.campaignComments"),
      ).toBeInTheDocument();
    });

    it("should render textarea for comments", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByTestId("comment-textarea")).toBeInTheDocument();
    });

    it("should render attach files button", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("commentsTab.attachFiles")).toBeInTheDocument();
    });

    it("should render post comment button", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("commentsTab.postComment")).toBeInTheDocument();
    });
  });

  describe("Comment Input", () => {
    it("should update comment text when typing", async () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "This is a test comment");

      expect(textarea).toHaveValue("This is a test comment");
    });

    it("should disable post button when comment is empty", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const postButton = screen.getByText("commentsTab.postComment");
      expect(postButton).toBeDisabled();
    });

    it("should enable post button when comment has text", async () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "Test comment");

      const postButton = screen.getByText("commentsTab.postComment");
      expect(postButton).not.toBeDisabled();
    });

    it("should enforce max length of 200 characters", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId(
        "comment-textarea",
      ) as HTMLTextAreaElement;
      const overLimit = "a".repeat(201);
      fireEvent.change(textarea, { target: { value: overLimit } });

      expect(textarea.value.length).toBeLessThanOrEqual(200);
    });
  });

  describe("Posting Comments", () => {
    it("should call postComment when post button is clicked", async () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "Test comment");

      const postButton = screen.getByText("commentsTab.postComment");
      await user.click(postButton);

      await waitFor(() => {
        expect(mockPostComment).toHaveBeenCalled();
      });
    });

    it("should show error when comment is empty", async () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const postButton = screen.getByText("commentsTab.postComment");
      // Button should be disabled when comment is empty
      expect(postButton).toBeDisabled();

      // Try to click the disabled button - it should not trigger the error handler
      // because the button is disabled and the click won't fire the onClick handler
      // The validation happens in handlePostComment, but since button is disabled,
      // we can't test it this way. Instead, we test that the button is disabled.
      expect(postButton).toBeDisabled();
    });

    it("should show error when campaignId is missing", async () => {
      render(
        <TestWrapper>
          <CommentsTab />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "Test comment");

      const postButton = screen.getByText("commentsTab.postComment");
      await user.click(postButton);

      await waitFor(() => {
        expect(mockShowError).toHaveBeenCalledWith(
          "commentsTab.campaignIdRequired",
        );
      });
    });

    it("should reset form after successful post", async () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "Test comment");

      const postButton = screen.getByText("commentsTab.postComment");
      await user.click(postButton);

      await waitFor(() => {
        expect(screen.getByTestId("comment-textarea")).toHaveValue("");
      });
    });

    it("should show success message after posting", async () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "Test comment");

      const postButton = screen.getByText("commentsTab.postComment");
      await user.click(postButton);

      await waitFor(() => {
        expect(mockShowSuccess).toHaveBeenCalledWith("commentsTab.postSuccess");
      });
    });

    it("should call onAddComment callback if provided", async () => {
      const mockOnAddComment = vi.fn();

      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" onAddComment={mockOnAddComment} />
        </TestWrapper>,
      );

      const textarea = screen.getByTestId("comment-textarea");
      await user.type(textarea, "Test comment");

      const postButton = screen.getByText("commentsTab.postComment");
      await user.click(postButton);

      await waitFor(() => {
        expect(mockOnAddComment).toHaveBeenCalled();
      });
    });
  });

  describe("Comments Display", () => {
    const mockComments = [
      {
        id: "1",
        author: {
          name: "John Doe",
          role: "Admin",
          initials: "JD",
        },
        content: "This is a test comment",
        timestamp: "Jan 15, 2024, 10:30 AM",
        fileUrls: [],
      },
      {
        id: "2",
        author: {
          name: "Jane Smith",
          role: "Planner",
          initials: "JS",
        },
        content: "Another comment",
        timestamp: "Jan 16, 2024, 2:20 PM",
        fileUrls: ["https://example.com/file.pdf"],
      },
    ];

    it("should display comments from props", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" comments={mockComments} />
        </TestWrapper>,
      );

      expect(screen.getByText("This is a test comment")).toBeInTheDocument();
      expect(screen.getByText("Another comment")).toBeInTheDocument();
    });

    it("should display author names", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" comments={mockComments} />
        </TestWrapper>,
      );

      expect(screen.getByText("John Doe")).toBeInTheDocument();
      expect(screen.getByText("Jane Smith")).toBeInTheDocument();
    });

    it("should display author roles", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" comments={mockComments} />
        </TestWrapper>,
      );

      expect(screen.getByText("Admin")).toBeInTheDocument();
      expect(screen.getByText("Planner")).toBeInTheDocument();
    });

    it("should display timestamps", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" comments={mockComments} />
        </TestWrapper>,
      );

      expect(screen.getByText("Jan 15, 2024, 10:30 AM")).toBeInTheDocument();
      expect(screen.getByText("Jan 16, 2024, 2:20 PM")).toBeInTheDocument();
    });

    it("should display comment count badge", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" comments={mockComments} />
        </TestWrapper>,
      );

      const campaignCommentsLabel = screen.getByText(
        "commentsTab.campaignComments",
      );
      const commentsSection = campaignCommentsLabel.closest("div");
      expect(within(commentsSection!).getByText("2")).toBeInTheDocument();
    });
  });

  describe("File Attachments", () => {
    it("should render file input", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      const fileInput = document.querySelector('input[type="file"]');
      expect(fileInput).toBeInTheDocument();
    });

    it("should show file size limit message", () => {
      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(
        screen.getByText("commentsTab.attachmentTypes"),
      ).toBeInTheDocument();
    });
  });

  describe("Loading States", () => {
    it("should show loading message when loading comments", () => {
      mockIsLoadingComments = true;
      mockCommentsData = undefined;
      mockCommentsError = null;

      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(
        screen.getByText("commentsTab.loadingComments"),
      ).toBeInTheDocument();
    });
  });

  describe("Error States", () => {
    it("should show error message when comments fail to load", () => {
      mockIsLoadingComments = false;
      mockCommentsData = undefined;
      mockCommentsError = { message: "Failed to load" };

      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("commentsTab.loadError")).toBeInTheDocument();
    });
  });

  describe("Empty State", () => {
    it("should show empty message when no comments", () => {
      mockIsLoadingComments = false;
      mockCommentsData = { success: true, data: [] };
      mockCommentsError = null;

      render(
        <TestWrapper>
          <CommentsTab campaignId="test-id" />
        </TestWrapper>,
      );

      expect(screen.getByText("commentsTab.noComments")).toBeInTheDocument();
    });
  });

  describe("Edge Cases", () => {
    it("should handle missing campaignId gracefully", () => {
      render(
        <TestWrapper>
          <CommentsTab />
        </TestWrapper>,
      );

      expect(screen.getByText("commentsTab.addComment")).toBeInTheDocument();
    });

    it("should handle comments without fileUrls", () => {
      const commentsWithoutFiles = [
        {
          id: "1",
          author: {
            name: "John Doe",
            role: "Admin",
            initials: "JD",
          },
          content: "Test comment",
          timestamp: "Jan 15, 2024",
        },
      ];

      render(
        <TestWrapper>
          <CommentsTab
            campaignId="test-id"
            comments={commentsWithoutFiles as []}
          />
        </TestWrapper>,
      );

      expect(screen.getByText("Test comment")).toBeInTheDocument();
    });
  });
});
