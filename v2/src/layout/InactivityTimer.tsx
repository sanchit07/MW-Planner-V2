import { Modal } from "@components/ui/Modal";
import { useInactivityTimer } from "@hooks/useInactivityTimer";
import { useTranslate } from "@tolgee/react";
import React from "react";

/** Warns the user at 29 minutes idle and auto-logs-out at 30 with no response. */
export const InactivityTimer: React.FC = () => {
  const { t: tCommon } = useTranslate(["common"]);
  const {
    isWarningOpen,
    remainingSeconds,
    handleStaySignedIn,
    handleSignOutNow,
    isImpersonating,
    impersonatedCompanyName,
  } = useInactivityTimer();

  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;
  const countdownLabel = `${minutes}:${seconds.toString().padStart(2, "0")}`;

  return (
    <Modal
      isOpen={isWarningOpen}
      onClose={handleStaySignedIn}
      title={tCommon("inactivity.title")}
      primaryButtonText={
        isImpersonating
          ? tCommon("inactivity.keep_impersonating")
          : tCommon("inactivity.stay_signed_in")
      }
      secondaryButtonText={tCommon("inactivity.sign_out")}
      onPrimaryAction={handleStaySignedIn}
      onSecondaryAction={handleSignOutNow}
      showCloseButton={false}
      size="sm"
    >
      <div className="space-y-2">
        <p className="text-sm text-mw-neutral-700">
          {isImpersonating
            ? tCommon("inactivity.message_impersonating", {
                company: impersonatedCompanyName,
              })
            : tCommon("inactivity.message")}
        </p>
        <p className="text-sm font-semibold text-mw-neutral-700">
          {tCommon("inactivity.countdown", { time: countdownLabel })}
        </p>
      </div>
    </Modal>
  );
};

export default InactivityTimer;
