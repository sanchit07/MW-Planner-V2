import { Modal } from "@components/ui/Modal";
import { Textarea } from "@components/ui/Textarea";
import { useTranslate } from "@tolgee/react";
import { useState } from "react";

export type PasteReferenceIdsMode = "add" | "replace";

interface PasteReferenceIdsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (referenceIds: string[], mode: PasteReferenceIdsMode) => void;
}

/** Parses a pasted block of reference IDs separated by commas, whitespace, or newlines. */
function parseReferenceIds(raw: string): string[] {
  return Array.from(
    new Set(
      raw
        .split(/[\s,]+/)
        .map((id) => id.trim())
        .filter(Boolean),
    ),
  );
}

/** Bulk-select inventories by pasting their reference IDs, matched server-side against the full campaign inventory set. */
const PasteReferenceIdsModal: React.FC<PasteReferenceIdsModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [value, setValue] = useState("");
  const [mode, setMode] = useState<PasteReferenceIdsMode>("add");

  const handleClose = () => {
    setValue("");
    setMode("add");
    onClose();
  };

  const handleSubmit = () => {
    const ids = parseReferenceIds(value);
    if (ids.length === 0) return;
    onSubmit(ids, mode);
    handleClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={tCampaigns("inventories.manual.pasteIds.title")}
      primaryButtonText={tCampaigns("inventories.manual.pasteIds.submit")}
      secondaryButtonText={tCampaigns("inventories.manual.pasteIds.cancel")}
      onPrimaryAction={handleSubmit}
      onSecondaryAction={handleClose}
      size="md"
    >
      <div className="space-y-3">
        <p className="text-sm text-mw-neutral-500">
          {tCampaigns("inventories.manual.pasteIds.description")}
        </p>
        <Textarea
          id="paste-reference-ids-textarea"
          placeholder={tCampaigns("inventories.manual.pasteIds.placeholder")}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={6}
        />
        <div className="flex items-center gap-4 text-sm">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="radio"
              name="paste-ids-mode"
              checked={mode === "add"}
              onChange={() => setMode("add")}
            />
            {tCampaigns("inventories.manual.pasteIds.modeAdd")}
          </label>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="radio"
              name="paste-ids-mode"
              checked={mode === "replace"}
              onChange={() => setMode("replace")}
            />
            {tCampaigns("inventories.manual.pasteIds.modeReplace")}
          </label>
        </div>
      </div>
    </Modal>
  );
};

export default PasteReferenceIdsModal;
