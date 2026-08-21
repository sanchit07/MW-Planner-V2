import { Button } from "@components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@components/ui/card";
import { Textarea, MentionOption } from "@components/ui/Textarea";
import { Tooltip } from "@components/ui/Tooltip";
import { useAnnounce } from "@hooks/useAnnounce";
import {
  usePostCommentMutation,
  useGetCommentsQuery,
  CampaignComment,
} from "@services/campaign/campaignDetailsSlice";
import { useTranslate } from "@tolgee/react";
import {
  Paperclip,
  Send,
  Trash2,
  FileImage,
  FileText,
  Eye,
  Download,
} from "lucide-react";
import { useState, useRef, useMemo, useCallback } from "react";
// import { useAppSelector } from "src/store";

export interface Comment {
  id: string;
  author: {
    name: string;
    role: string;
    avatar?: string;
    initials: string;
  };
  content: string;
  timestamp: string;
  fileUrls?: string[];
}

interface CommentsTabProps {
  campaignId?: string;
  comments?: Comment[];
  onAddComment?: (comment: string, mentions?: MentionOption[]) => void;
  mentionOptions?: MentionOption[];
}

const CommentsTab: React.FC<CommentsTabProps> = ({
  campaignId,
  comments: propComments = [],
  onAddComment,
  mentionOptions = [],
}) => {
  const [commentText, setCommentText] = useState("");
  const [selectedMentions, setSelectedMentions] = useState<MentionOption[]>([]);
  const [attachedFiles, setAttachedFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  // const user = useAppSelector((s) => s.profile.profile);
  const maxLength = 200;

  // API hooks
  const [postComment, { isLoading: isPostingComment }] =
    usePostCommentMutation();
  const { showSuccess, showError } = useAnnounce();
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const { t: tCommon } = useTranslate(["common"]);

  const getInitials = (name: string): string => {
    return name
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2);
  };

  // Fetch comments from API
  const {
    data: commentsData,
    isLoading: isLoadingComments,
    error: commentsError,
    refetch: refetchComments,
  } = useGetCommentsQuery(campaignId || "", {
    skip: !campaignId, // Skip query if campaignId is not available
  });

  // Format timestamp for display
  const formatTimestamp = (dateString: string): string => {
    try {
      // Handle "YYYY-MM-DD HH:mm:ss" format
      const date = new Date(dateString.replace(" ", "T"));
      const month = tCommon(`calendar.monthNamesShort.${date.getMonth()}`);
      const day = date.getDate();
      const year = date.getFullYear();
      const dateStr = tCommon("calendar.formattedShortDate", {
        month,
        day,
        year,
      });
      const hours = date.getHours();
      const minutes = date.getMinutes().toString().padStart(2, "0");
      const isAM = hours < 12;
      const hour12 = hours % 12 || 12;
      const ampm = tCommon(isAM ? "calendar.am" : "calendar.pm");
      const timeStr = `${hour12}:${minutes} ${ampm}`;
      return tCommon("calendar.formattedDateTime", {
        date: dateStr,
        time: timeStr,
      });
    } catch {
      return dateString;
    }
  };

  // Format business type for display (e.g., "MEDIA_OPERATOR" -> "Media Operator")
  const formatBusinessType = (businessType: string): string => {
    return businessType
      .split("_")
      .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
      .join(" ");
  };

  // Helper function to get file type from URL
  const getFileType = (url: string): "image" | "pdf" => {
    const extension = url.split(".").pop()?.toLowerCase() || "";
    if (["jpg", "jpeg", "png", "svg"].includes(extension)) {
      return "image";
    }
    return "pdf";
  };

  // Helper function to get file name from URL
  const getFileNameFromUrl = (url: string): string => {
    try {
      const urlParts = url.split("/");
      return urlParts[urlParts.length - 1] || "file";
    } catch {
      return "file";
    }
  };

  // Helper function to download file
  const handleDownloadFile = async (url: string) => {
    window.open(url, "_blank");

    //Facing CORS error while downloading files directly, hence opening in new tab above.
    // try {
    //   const response = await fetch(url);
    //   const blob = await response.blob();
    //   const fileName = getFileNameFromUrl(url);
    //   const downloadUrl = window.URL.createObjectURL(blob);
    //   const link = document.createElement("a");
    //   link.href = downloadUrl;
    //   link.download = fileName;
    //   link.style.display = "none";
    //   document.body.appendChild(link);
    //   link.click();
    //   document.body.removeChild(link);
    //   window.URL.revokeObjectURL(downloadUrl);
    // } catch (error) {
    //   console.error("Error downloading file:", error);
    //   showError("Failed to download file. Please try again.");
    // }
  };

  // Transform API comments to component Comment format
  const transformApiComment = useCallback(
    (apiComment: CampaignComment, index: number): Comment => {
      return {
        id: `${apiComment.createdAt}-${index}`, // Generate unique ID from timestamp and index
        author: {
          name: apiComment.createdBy,
          role: formatBusinessType(apiComment.businessType ?? ""),
          avatar: undefined,
          initials: getInitials(apiComment.createdBy),
        },
        content: apiComment.comment,
        timestamp: formatTimestamp(apiComment.createdAt),
        fileUrls: apiComment.fileUrls,
      };
    },
    [],
  );

  // Get comments from API or props
  const comments = useMemo(() => {
    // Priority: propComments > API data
    if (propComments.length > 0) {
      return propComments;
    }

    if (commentsData?.data && commentsData.data.length > 0) {
      return commentsData.data.map((comment, index) =>
        transformApiComment(comment, index),
      );
    }

    return [];
  }, [propComments, commentsData, transformApiComment]);

  // Transform user companies into mention options
  // const companyMentionOptions: MentionOption[] =
  //   user?.companies?.map((company) => ({
  //     id: company.id,
  //     label: company.name,
  //     value: company.id,
  //   })) || [];
  const companyMentionOptions: MentionOption[] = [];
  // Use provided mentionOptions, then company options, then fallback to empty array
  const availableMentions =
    mentionOptions.length > 0
      ? mentionOptions
      : companyMentionOptions.length > 0
        ? companyMentionOptions
        : [];

  const handleCommentChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newValue = e.target.value;
    if (newValue.length <= maxLength) {
      setCommentText(newValue);
    }
  };

  const handleMentionSelect = (mention: MentionOption) => {
    if (!selectedMentions.find((m) => m.id === mention.id)) {
      setSelectedMentions([...selectedMentions, mention]);
    }
  };

  const handlePostComment = async () => {
    // Validate inputs
    if (!commentText.trim()) {
      showError(tCampaigns("commentsTab.emptyError"));
      return;
    }

    if (!campaignId) {
      showError(tCampaigns("commentsTab.campaignIdRequired"));
      return;
    }

    try {
      // Call the API
      await postComment({
        campaignId: campaignId,
        request: {
          comment: commentText,
          taggedCompanyIds:
            selectedMentions.length > 0
              ? selectedMentions.map((m) => m.id)
              : undefined,
        },
        files: attachedFiles.length > 0 ? attachedFiles : undefined,
      }).unwrap();

      // Success - show notification
      showSuccess(tCampaigns("commentsTab.postSuccess"));

      // Reset form
      setCommentText("");
      setSelectedMentions([]);
      setAttachedFiles([]);

      // Refetch comments to show the new comment
      if (campaignId) {
        refetchComments();
      }

      // Call the optional callback if provided
      if (onAddComment) {
        onAddComment(commentText, selectedMentions);
      }
    } catch (error) {
      // Error handling
      console.error("Failed to post comment:", error);
      showError(tCampaigns("commentsTab.postError"));
    }
  };

  const handleAttachFiles = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      const fileArray = Array.from(files);
      // Validate file size (5MB max)
      const maxSize = 5 * 1024 * 1024; // 5MB
      const validFiles = fileArray.filter((file) => {
        if (file.size > maxSize) {
          alert(
            tCampaigns("commentsTab.fileSizeError", { fileName: file.name }),
          );
          return false;
        }
        return true;
      });
      setAttachedFiles((prev) => [...prev, ...validFiles]);
    }
    // Reset input value to allow selecting the same file again
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleRemoveFile = (index: number) => {
    setAttachedFiles((prev) => prev.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-6">
      {/* Add Comment Section */}
      <Card>
        <CardHeader className="p-4">
          <CardTitle className="text-base font-medium border-b border-container-border pb-4 leading-5">
            {tCampaigns("commentsTab.addComment")}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Textarea
            value={commentText}
            onChange={handleCommentChange}
            placeholder={tCampaigns("commentsTab.placeholder")}
            rows={4}
            className="resize-none"
            enableMentions={true}
            mentionKey="@"
            mentionOptions={availableMentions}
            onMentionSelect={handleMentionSelect}
            showCharCount={true}
            maxLength={maxLength}
            currentLength={commentText.length}
            disabled={isPostingComment}
          />
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept=".pdf,.png,.jpg,.jpeg"
                onChange={handleFileChange}
                className="hidden"
              />
              <Button
                variant="outline"
                size="sm"
                onClick={handleAttachFiles}
                type="button"
                disabled={isPostingComment}
                className="flex items-center gap-2 border-mw-primary-500 text-mw-primary-500 hover:bg-mw-primary-50"
              >
                <Paperclip className="h-4 w-4" />
                {tCampaigns("commentsTab.attachFiles")}
              </Button>
              <span className="text-sm text-mw-neutral-500">
                {tCampaigns("commentsTab.attachmentTypes")}
              </span>
            </div>
            <Button
              variant="primary"
              size="sm"
              onClick={handlePostComment}
              disabled={!commentText.trim() || isPostingComment}
              className="flex items-center gap-2"
            >
              <Send className="h-4 w-4" />
              {isPostingComment
                ? tCampaigns("commentsTab.posting")
                : tCampaigns("commentsTab.postComment")}
            </Button>
          </div>
          {/* Show attached files */}
          {attachedFiles.length > 0 && (
            <div className="space-y-2">
              {attachedFiles.map((file, index) => (
                <div
                  key={index}
                  className="flex items-center gap-2 text-sm text-mw-neutral-700 dark:text-mw-neutral-300 border border-container-border rounded-md p-2"
                >
                  <Paperclip className="h-3 w-3" />
                  <span className="flex-1 truncate">{file.name}</span>
                  <button
                    onClick={() => handleRemoveFile(index)}
                    className="text-mw-error-500 hover:text-mw-error-600 text-xs cursor-pointer"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Campaign Comments Section */}
      <Card>
        <CardHeader className="p-4">
          <CardTitle className="text-base font-medium border-b border-container-border pb-4 leading-5">
            <div className="flex items-center gap-2">
              {tCampaigns("commentsTab.campaignComments")}
              {comments.length > 0 && (
                <span className="px-1.5 py-0.5 bg-mw-primary-500 text-white text-xs font-medium rounded">
                  {comments.length}
                </span>
              )}
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoadingComments ? (
            <div className="text-center py-8 text-mw-neutral-500">
              {tCampaigns("commentsTab.loadingComments")}
            </div>
          ) : commentsError ? (
            <div className="text-center py-8 text-mw-error-500">
              {tCampaigns("commentsTab.loadError")}
            </div>
          ) : comments.length === 0 ? (
            <div className="text-center py-8 text-mw-neutral-500">
              {tCampaigns("commentsTab.noComments")}
            </div>
          ) : (
            <div className="space-y-6">
              {comments.map((comment) => (
                <Card key={comment.id} className="flex gap-4 p-4">
                  {/* Avatar */}
                  <div className="shrink-0">
                    <div className="w-10 h-10 rounded-full bg-mw-primary-100 dark:bg-mw-primary-200 flex items-center justify-center">
                      {comment.author.avatar ? (
                        <img
                          src={comment.author.avatar}
                          alt={comment.author.name}
                          className="w-10 h-10 rounded-full"
                        />
                      ) : (
                        <span className="text-mw-primary-600 dark:text-mw-primary-700 text-sm font-medium">
                          {comment.author.initials ||
                            getInitials(comment.author.name)}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Comment Content */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-4 mb-2">
                      <div>
                        <p className="font-semibold text-sm text-mw-neutral-700 dark:text-white leading-4">
                          {comment.author.name}
                        </p>
                        <p className="text-sm font-normal leading-4 text-mw-neutral-700 dark:text-mw-neutral-400">
                          {comment.author.role}
                        </p>
                      </div>
                      <span className="text-xs font-normal leading-4 text-mw-neutral-700 dark:text-mw-neutral-400 whitespace-nowrap">
                        {comment.timestamp}
                      </span>
                    </div>
                    {/* Attachment Chips */}
                    {comment.fileUrls && comment.fileUrls.length > 0 && (
                      <div className="flex flex-wrap gap-2 mb-3">
                        {comment.fileUrls.map((attachment, index) => {
                          const fileType = getFileType(attachment);
                          const fileName = getFileNameFromUrl(attachment);
                          return (
                            <div
                              key={`${comment.id}-${index}`}
                              className="group relative flex items-center gap-2 px-3 py-1.5 border border-container-border rounded-md transition-colors cursor-pointer"
                            >
                              {/* File Icon */}
                              {fileType === "image" ? (
                                <FileImage className="h-4 w-4 text-mw-neutral-600 flex-shrink-0" />
                              ) : (
                                <FileText className="h-4 w-4 text-mw-neutral-600 flex-shrink-0" />
                              )}
                              {/* File Name */}
                              <Tooltip content={fileName}>
                                <span className="text-sm text-mw-neutral-700 dark:text-mw-neutral-300 font-medium max-w-35 truncate">
                                  {fileName}
                                </span>
                              </Tooltip>
                              {/* Action Buttons */}
                              <div className="flex items-center ">
                                <Button
                                  size="xsm"
                                  variant="ghost"
                                  onClick={() =>
                                    window.open(attachment, "_blank")
                                  }
                                  title={tCampaigns(
                                    "commentsTab.viewAttachment",
                                  )}
                                >
                                  <Eye className="h-3.5 w-3.5 text-mw-neutral-600" />
                                </Button>
                                <Button
                                  size="xsm"
                                  variant="ghost"
                                  onClick={() => handleDownloadFile(attachment)}
                                  title={tCampaigns(
                                    "commentsTab.downloadAttachment",
                                  )}
                                >
                                  <Download className="h-3.5 w-3.5 text-mw-neutral-600" />
                                </Button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                    <p className="text-sm font-normal leading-4 text-mw-neutral-500 dark:text-mw-neutral-300 whitespace-pre-wrap">
                      {comment.content}
                    </p>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default CommentsTab;
