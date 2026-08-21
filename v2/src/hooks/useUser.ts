import { useLazyGetUserByIdQuery } from "@services/user/userSlice";
import { setUserProfile } from "@services/user/userSlice";
import { useCallback } from "react";

import { useAppSelector, useAppDispatch } from "../store";

export const useUser = () => {
  const dispatch = useAppDispatch();
  const userState = useAppSelector((state) => state.profile);
  const auth = useAppSelector((state) => state.auth);
  const [getUserById, { isLoading, error }] = useLazyGetUserByIdQuery();

  const refetchUser = useCallback(async () => {
    if (userState.profile?.id) {
      try {
        const result = await getUserById(userState.profile.id).unwrap();
        dispatch(setUserProfile(result));
      } catch (err) {
        console.error("Failed to refetch user data:", err);
      }
    }
  }, [dispatch, userState.profile?.id, getUserById]);

  const hasUserData = Boolean(userState.profile);

  return {
    profile: userState.profile,
    isLoading,
    error: error ? "Failed to load user data" : null,
    refetchUser,
    hasUserData,
    isAuthenticated: auth.isAuthenticated,
  };
};
