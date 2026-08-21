import { Button } from "@components/ui/Button";
import { Input } from "@components/ui/Input";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { useAnnounce } from "@hooks/useAnnounce";
import { useTranslate } from "@tolgee/react";
import { Copy } from "lucide-react";
import React from "react";

import WhatsAppIcon from "../../../assets/icon/whatsapp.png";

interface RecentlySharedContact {
  email: string;
  initials?: string;
  avatar?: string;
}

interface ShareModalDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  shareUrl: string;
  onWhatsAppShare?: () => void;
  onInternalShare?: (emails: string[]) => void;
  recentlyShared?: RecentlySharedContact[];
  onRemoveRecentContact?: (email: string) => void;
}

const ShareModalDrawer: React.FC<ShareModalDrawerProps> = ({
  isOpen,
  onClose,
  shareUrl,
  onWhatsAppShare,
}) => {
  const { showSuccess } = useAnnounce();
  const { t: tCampaigns } = useTranslate(["campaigns"]);

  const handleCopyLink = () => {
    navigator.clipboard.writeText(shareUrl);
    showSuccess(tCampaigns("shareModal.linkCopied"));
  };

  const handleWhatsAppShare = () => {
    const text = encodeURIComponent(`Check out this media plan: ${shareUrl}`);
    window.open(`https://wa.me/?text=${text}`, "_blank");
    onWhatsAppShare?.();
  };

  return (
    <ModalDrawer
      isOpen={isOpen}
      onClose={onClose}
      title={tCampaigns("shareModal.title")}
      size="md"
      position="right"
      showBackButton={false}
    >
      <div className="space-y-6">
        {/* Share Link Section */}
        <div className="space-y-2">
          <label className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-300">
            {tCampaigns("shareModal.shareLink")}
          </label>
          <div className="relative w-full">
            <Input
              value={shareUrl}
              readOnly
              className="w-full pr-10 text-sm bg-mw-neutral-50 dark:bg-mw-neutral-700"
            />
            <button
              onClick={handleCopyLink}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-mw-neutral-500 hover:text-mw-neutral-700 dark:hover:text-mw-neutral-300 transition-colors cursor-pointer"
            >
              <Copy className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Share via Section */}
        <div className="space-y-3">
          <label className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-300">
            {tCampaigns("shareModal.shareVia")}
          </label>
          <div className="flex gap-3">
            <Button
              onClick={handleWhatsAppShare}
              className="flex-1 flex items-center justify-start gap-2 bg-mw-success-50 hover:bg-mw-success-50 hover:opacity-100"
            >
              <img src={WhatsAppIcon} alt="WhatsApp" className="size-9" />
              <p className="text-sm font-semibold text-mw-success-500 leading-6">
                {tCampaigns("shareModal.whatsApp")}
              </p>
            </Button>
          </div>
        </div>
      </div>
    </ModalDrawer>
  );
};

export default ShareModalDrawer;
