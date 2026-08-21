import { createContext, useContext, useMemo, useState } from "react";

interface InlinePriceEditValue {
  /** Key of the row currently being edited, or null when none is. */
  editingKey: string | null;
  setEditingKey: (key: string | null) => void;
}

const InlinePriceEditContext = createContext<InlinePriceEditValue | null>(null);

/**
 * Keeps at most one proposed-price cell in edit mode. Opening a second cell
 * closes the first, which discards its unsaved draft.
 *
 * Optional: a cell rendered outside this provider falls back to its own local
 * edit state.
 */
export const InlinePriceEditProvider: React.FC<{
  children: React.ReactNode;
}> = ({ children }) => {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const value = useMemo(() => ({ editingKey, setEditingKey }), [editingKey]);

  return (
    <InlinePriceEditContext.Provider value={value}>
      {children}
    </InlinePriceEditContext.Provider>
  );
};

/**
 * Edit state for one cell. Uses the shared provider when there is one so only a
 * single cell can be open; otherwise the cell manages its own state.
 */
export const useInlinePriceEditing = (rowKey: string) => {
  const shared = useContext(InlinePriceEditContext);
  const [localEditing, setLocalEditing] = useState(false);

  const isEditing = shared ? shared.editingKey === rowKey : localEditing;

  const setEditing = (editing: boolean) => {
    if (shared) {
      shared.setEditingKey(editing ? rowKey : null);
      return;
    }
    setLocalEditing(editing);
  };

  return { isEditing, setEditing };
};
