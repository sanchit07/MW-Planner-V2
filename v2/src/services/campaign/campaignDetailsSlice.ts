import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { createApi } from "@reduxjs/toolkit/query/react";

// Comment interfaces - matches actual API response
export interface CampaignComment {
  comment: string;
  createdBy: string;
  createdAt: string;
  businessType: string;
  fileUrls?: string[];
}

// Request interfaces
export interface PostCommentRequest {
  comment: string;
  taggedCompanyIds?: string[];
}

export interface PostCommentParams {
  campaignId: string;
  request: PostCommentRequest;
  files?: File[];
}

export interface PostCommentResponse {
  id: string;
  comment: string;
  taggedCompanyIds?: string[];
  createdAt: string;
  createdBy: string;
}

// Campaign Details state
export interface CampaignDetailsState {
  comments: CampaignComment[];
  isLoadingComments: boolean;
  commentsError: string | null;
  isPostingComment: boolean;
  postCommentError: string | null;
}

const initialState: CampaignDetailsState = {
  comments: [],
  isLoadingComments: false,
  commentsError: null,
  isPostingComment: false,
  postCommentError: null,
};

// Campaign Details slice
export const campaignDetailsSlice = createSlice({
  name: "campaignDetails",
  initialState,
  reducers: {
    setComments: (state, action: PayloadAction<CampaignComment[]>) => {
      state.comments = action.payload;
    },
    addComment: (state, action: PayloadAction<CampaignComment>) => {
      state.comments.unshift(action.payload);
    },
    setIsLoadingComments: (state, action: PayloadAction<boolean>) => {
      state.isLoadingComments = action.payload;
    },
    setCommentsError: (state, action: PayloadAction<string | null>) => {
      state.commentsError = action.payload;
    },
    setIsPostingComment: (state, action: PayloadAction<boolean>) => {
      state.isPostingComment = action.payload;
    },
    setPostCommentError: (state, action: PayloadAction<string | null>) => {
      state.postCommentError = action.payload;
    },
    resetCampaignDetailsState: () => initialState,
  },
});

export const {
  setComments,
  addComment,
  setIsLoadingComments,
  setCommentsError,
  setIsPostingComment,
  setPostCommentError,
  resetCampaignDetailsState,
} = campaignDetailsSlice.actions;

// Campaign Details API
export const campaignDetailsApi = createApi({
  reducerPath: "campaignDetailsApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["CampaignComment"],
  endpoints: (builder) => ({
    postComment: builder.mutation<
      SuccessResponse<PostCommentResponse>,
      PostCommentParams
    >({
      query: ({ campaignId, request, files }) => {
        const formData = new FormData();

        // Add the request object as a JSON blob
        formData.append(
          "request",
          new Blob([JSON.stringify(request)], { type: "application/json" }),
        );

        // Add files if provided
        if (files && files.length > 0) {
          files.forEach((file) => {
            formData.append("files", file);
          });
        }

        return {
          url: `/campaign-inventory/${campaignId}/comment`,
          method: "POST",
          data: formData,
        };
      },
      invalidatesTags: ["CampaignComment"],
    }),
    getComments: builder.query<SuccessResponse<CampaignComment[]>, string>({
      query: (campaignId) => ({
        url: `/campaign-inventory/${campaignId}/comments`,
        method: "GET",
      }),
      providesTags: ["CampaignComment"],
    }),
  }),
});

export const {
  usePostCommentMutation,
  useGetCommentsQuery,
  useLazyGetCommentsQuery,
} = campaignDetailsApi;

export default campaignDetailsSlice.reducer;
