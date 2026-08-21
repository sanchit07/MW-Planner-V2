import { Button } from "@components/ui/Button";
import { useLazyLogoutQuery } from "@services/auth/authSlice";
import { useAppDispatch, useAppSelector } from "@store";
import { ShieldX } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { handleLogout } from "./OAuthCallbackPage";

export default function Unauthorized() {
  const dispatch = useAppDispatch();
  const [userLogout] = useLazyLogoutQuery();
  const refreshToken = useAppSelector((state) => state.auth.refreshToken);
  const navigate = useNavigate();

  const handleTryDifferentAccount = async () => {
    const response = await userLogout({
      refresh_token: refreshToken || "",
    });

    if (response.data?.success) {
      handleLogout(dispatch);
      navigate("/login", { replace: true });
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <div className="text-center max-w-md px-6">
        <ShieldX className="h-12 w-12 mx-auto mb-4 text-muted-foreground" />
        <h1 className="text-xl font-semibold mb-2">Access Denied</h1>
        <p className="text-muted-foreground mb-6">
          Your account does not have permission to access this application.
          Please contact your administrator or try a different account.
        </p>
        <Button onClick={handleTryDifferentAccount}>
          Try a different account
        </Button>
      </div>
    </div>
  );
}
