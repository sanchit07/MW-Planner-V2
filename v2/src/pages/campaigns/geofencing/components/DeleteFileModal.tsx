import { Modal } from "@components/ui/Modal";
import { useTranslate } from "@tolgee/react";
import React from "react";

interface DeleteFileModalProps {
  isOpen: boolean;
  fileName: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export const DeleteFileModal: React.FC<DeleteFileModalProps> = ({
  isOpen,
  fileName,
  onConfirm,
  onCancel,
}) => {
  const { t } = useTranslate(["campaigns"]);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onCancel}
      title={t("geofencingDrawer.deleteFile.title")}
      primaryButtonText={t("geofencingDrawer.deleteFile.confirm")}
      secondaryButtonText={t("geofencingDrawer.deleteFile.cancel")}
      onPrimaryAction={onConfirm}
      onSecondaryAction={onCancel}
      primaryButtonVariant="danger"
      size="sm"
    >
      <p>
        {t("geofencingDrawer.deleteFile.message")} -{" "}
        <strong>
          "{fileName || t("geofencingDrawer.deleteFile.thisFile")}"
        </strong>
        ?
      </p>
    </Modal>
  );
};
