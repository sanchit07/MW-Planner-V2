import { describe, expect, it } from "vitest";

import {
  campaignDetailsApi,
  CampaignComment,
  CampaignDetailsState,
  addComment,
  resetCampaignDetailsState,
  setComments,
  setCommentsError,
  setIsLoadingComments,
  setIsPostingComment,
  setPostCommentError,
} from "../campaignDetailsSlice";
import reducer from "../campaignDetailsSlice";

const initialState: CampaignDetailsState = {
  comments: [],
  isLoadingComments: false,
  commentsError: null,
  isPostingComment: false,
  postCommentError: null,
};

/** Minimal valid CampaignComment for tests */
function makeComment(
  overrides: Partial<CampaignComment> = {},
): CampaignComment {
  return {
    comment: "Test comment",
    createdBy: "user-1",
    createdAt: "2026-01-01T00:00:00Z",
    businessType: "AGENCY",
    ...overrides,
  };
}

describe("campaignDetailsSlice", () => {
  describe("initial state", () => {
    it("returns the initial state when called with undefined", () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const state = reducer(undefined as any, { type: "@@INIT" });

      expect(state).toEqual(initialState);
    });
  });

  describe("setComments", () => {
    it("replaces the comments array with the payload", () => {
      const comments = [makeComment({ comment: "First" })];
      const state = reducer(initialState, setComments(comments));

      expect(state.comments).toHaveLength(1);
      expect(state.comments[0].comment).toBe("First");
    });

    it("clears the comments array when given an empty array", () => {
      const populated: CampaignDetailsState = {
        ...initialState,
        comments: [makeComment()],
      };

      const state = reducer(populated, setComments([]));

      expect(state.comments).toHaveLength(0);
    });

    it("does not affect other state fields", () => {
      const withFlags: CampaignDetailsState = {
        ...initialState,
        isLoadingComments: true,
        commentsError: "err",
      };

      const state = reducer(withFlags, setComments([makeComment()]));

      expect(state.isLoadingComments).toBe(true);
      expect(state.commentsError).toBe("err");
    });
  });

  describe("addComment", () => {
    it("prepends the new comment to the front of the array", () => {
      const existing = makeComment({ comment: "Existing" });
      const withExisting: CampaignDetailsState = {
        ...initialState,
        comments: [existing],
      };

      const newComment = makeComment({ comment: "New" });
      const state = reducer(withExisting, addComment(newComment));

      expect(state.comments).toHaveLength(2);
      expect(state.comments[0].comment).toBe("New");
      expect(state.comments[1].comment).toBe("Existing");
    });

    it("adds comment to an empty array", () => {
      const newComment = makeComment();
      const state = reducer(initialState, addComment(newComment));

      expect(state.comments).toHaveLength(1);
      expect(state.comments[0]).toEqual(newComment);
    });

    it("preserves all comment fields", () => {
      const comment = makeComment({
        comment: "With files",
        fileUrls: ["https://example.com/file.pdf"],
      });

      const state = reducer(initialState, addComment(comment));

      expect(state.comments[0].fileUrls).toEqual([
        "https://example.com/file.pdf",
      ]);
    });
  });

  describe("setIsLoadingComments", () => {
    it("sets isLoadingComments to true", () => {
      const state = reducer(initialState, setIsLoadingComments(true));

      expect(state.isLoadingComments).toBe(true);
    });

    it("sets isLoadingComments to false", () => {
      const loading: CampaignDetailsState = {
        ...initialState,
        isLoadingComments: true,
      };

      const state = reducer(loading, setIsLoadingComments(false));

      expect(state.isLoadingComments).toBe(false);
    });

    it("does not affect other state fields", () => {
      const state = reducer(initialState, setIsLoadingComments(true));

      expect(state.comments).toEqual([]);
      expect(state.commentsError).toBeNull();
    });
  });

  describe("setCommentsError", () => {
    it("sets an error message string", () => {
      const state = reducer(
        initialState,
        setCommentsError("Failed to load comments"),
      );

      expect(state.commentsError).toBe("Failed to load comments");
    });

    it("clears the error by setting null", () => {
      const withError: CampaignDetailsState = {
        ...initialState,
        commentsError: "Some error",
      };

      const state = reducer(withError, setCommentsError(null));

      expect(state.commentsError).toBeNull();
    });
  });

  describe("setIsPostingComment", () => {
    it("sets isPostingComment to true", () => {
      const state = reducer(initialState, setIsPostingComment(true));

      expect(state.isPostingComment).toBe(true);
    });

    it("sets isPostingComment to false", () => {
      const posting: CampaignDetailsState = {
        ...initialState,
        isPostingComment: true,
      };

      const state = reducer(posting, setIsPostingComment(false));

      expect(state.isPostingComment).toBe(false);
    });
  });

  describe("setPostCommentError", () => {
    it("sets a post comment error string", () => {
      const state = reducer(initialState, setPostCommentError("Post failed"));

      expect(state.postCommentError).toBe("Post failed");
    });

    it("clears the post comment error by setting null", () => {
      const withError: CampaignDetailsState = {
        ...initialState,
        postCommentError: "Post failed",
      };

      const state = reducer(withError, setPostCommentError(null));

      expect(state.postCommentError).toBeNull();
    });
  });

  describe("resetCampaignDetailsState", () => {
    it("resets all state fields back to initial values", () => {
      const dirty: CampaignDetailsState = {
        comments: [makeComment()],
        isLoadingComments: true,
        commentsError: "error",
        isPostingComment: true,
        postCommentError: "post error",
      };

      const state = reducer(dirty, resetCampaignDetailsState());

      expect(state).toEqual(initialState);
    });

    it("is idempotent — resetting already-initial state returns initial state", () => {
      const state = reducer(initialState, resetCampaignDetailsState());

      expect(state).toEqual(initialState);
    });
  });

  describe("campaignDetailsApi — endpoint registration", () => {
    it("registers the postComment endpoint", () => {
      expect(campaignDetailsApi.endpoints).toHaveProperty("postComment");
    });

    it("registers the getComments endpoint", () => {
      expect(campaignDetailsApi.endpoints).toHaveProperty("getComments");
    });

    it("exposes standard RTK Query interface on postComment", () => {
      const endpoint = campaignDetailsApi.endpoints.postComment;

      expect(typeof endpoint.initiate).toBe("function");
      expect(typeof endpoint.select).toBe("function");
    });

    it("exposes standard RTK Query interface on getComments", () => {
      const endpoint = campaignDetailsApi.endpoints.getComments;

      expect(typeof endpoint.initiate).toBe("function");
      expect(typeof endpoint.select).toBe("function");
    });
  });
});
