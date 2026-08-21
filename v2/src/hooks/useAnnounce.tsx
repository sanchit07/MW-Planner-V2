import { TriangleAlert } from "lucide-react";
import { toast } from "react-toastify";

export const useAnnounce = () => {
  const showSuccess = (message: string) => {
    toast.success(message);
  };

  const showError = (message: string) => {
    toast.error(message);
  };

  const showWarning = (message: string) => {
    toast.warn(message, {
      icon: <TriangleAlert className="w-5 h-5" />,
    });
  };

  const showInfo = (message: string) => {
    toast.info(message);
  };

  return {
    showSuccess,
    showError,
    showWarning,
    showInfo,
  };
};
