import { Badge } from "@components/ui/Badge";
import { Button } from "@components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@components/ui/card";
import { ModalDrawer } from "@components/ui/ModalDrawer";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@components/ui/Tabs";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  changePasswordSchema,
  ChangePasswordValues,
} from "@schemas/profile/profile.schema";
import { useSetPasswordMutation } from "@services/account/accountApi";
import { useAppSelector } from "@store";
import { useTranslate } from "@tolgee/react";
import { Check, Eye, EyeOff, KeyRound, Shield, User, X } from "lucide-react";
import React, { useState } from "react";
import { useForm, useWatch } from "react-hook-form";

const ProfilePage: React.FC = () => {
  const { t } = useTranslate(["common"]);
  const [activeTab, setActiveTab] = useState("profile");
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const user = useAppSelector((state) => state.profile.profile);
  const [setPassword] = useSetPasswordMutation();

  const fullName = user
    ? `${user.first_name ?? ""} ${user.last_name ?? ""}`.trim()
    : "";
  const initials = user
    ? `${user.first_name?.[0] ?? ""}${user.last_name?.[0] ?? ""}`.toUpperCase()
    : "";
  const companyName = user?.current_company?.name ?? "";

  const {
    register: registerPwd,
    handleSubmit: handleSubmitPwd,
    reset: resetPwd,
    control: controlPwd,
    formState: { errors: pwdErrors, isSubmitting: isPwdSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
  });

  const watchedNewPassword = useWatch({
    control: controlPwd,
    name: "newPassword",
    defaultValue: "",
  });
  const watchedConfirmPassword = useWatch({
    control: controlPwd,
    name: "confirmPassword",
    defaultValue: "",
  });

  const pwdChecks = {
    length: watchedNewPassword.length >= 8,
    upper: /[A-Z]/.test(watchedNewPassword),
    lower: /[a-z]/.test(watchedNewPassword),
    number: /[0-9]/.test(watchedNewPassword),
  };
  const pwdStrength = Object.values(pwdChecks).filter(Boolean).length;
  const strengthLabels = [
    "",
    t("profile.changePassword.strength.weak"),
    t("profile.changePassword.strength.fair"),
    t("profile.changePassword.strength.good"),
    t("profile.changePassword.strength.strong"),
  ];
  const strengthLabel = strengthLabels[pwdStrength];
  const strengthColor = [
    "",
    "bg-red-400",
    "bg-orange-400",
    "bg-yellow-400",
    "bg-green-500",
  ][pwdStrength];
  const passwordsMatch =
    watchedNewPassword.length > 0 &&
    watchedConfirmPassword.length > 0 &&
    watchedNewPassword === watchedConfirmPassword;

  const closePasswordModal = () => {
    setPasswordModalOpen(false);
    resetPwd();
    setShowNewPassword(false);
    setShowConfirmPassword(false);
  };

  const onChangePassword = async (values: ChangePasswordValues) => {
    if (!user?.id) return;
    await setPassword({ id: user.id, password: values.newPassword }).unwrap();
    closePasswordModal();
  };

  return (
    <div className="h-full overflow-y-auto">
      <div className="max-w-[65rem] mx-auto py-10 px-6">
        <h1 className="text-2xl font-semibold mb-0.5">
          {t("profile.settingsTitle")}
        </h1>
        <p className="text-mw-neutral-600 dark:text-mw-neutral-300 mb-4">
          {t("profile.description")}
        </p>

        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="w-full">
            <TabsTrigger value="profile">
              {t("profile.tabs.profile")}
            </TabsTrigger>
            <TabsTrigger value="account">
              {t("profile.tabs.account")}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="profile" className="mt-4">
            <Card>
              <CardHeader className="p-6 pb-4 border-b border-container-border">
                <CardTitle>
                  <div className="flex items-center gap-2">
                    <User className="w-5 h-5 text-mw-neutral-500" />
                    <span className="text-lg font-semibold">
                      {t("profile.personalInfo.title")}
                    </span>
                  </div>
                </CardTitle>
                <CardDescription>
                  <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 mt-1">
                    {t("profile.personalInfo.subtitle")}
                  </p>
                </CardDescription>
              </CardHeader>

              <CardContent className="p-6 pt-8 space-y-6">
                {/* Avatar */}
                <div className="flex items-center gap-4">
                  <div className="relative group w-16 h-16 flex-shrink-0">
                    <div className="w-16 h-16 rounded-full bg-mw-primary-100 dark:bg-mw-neutral-700 flex items-center justify-center transition-all duration-300 group-hover:scale-110 group-hover:shadow-lg group-hover:shadow-mw-primary-200 dark:group-hover:shadow-mw-primary-900/40 group-hover:ring-2 group-hover:ring-mw-primary-400 dark:group-hover:ring-mw-primary-500 ring-offset-2 ring-offset-white dark:ring-offset-mw-neutral-900">
                      {user?.avatar ? (
                        <img
                          src={user.avatar}
                          alt={fullName}
                          className="w-16 h-16 rounded-full object-cover"
                        />
                      ) : (
                        <span className="text-xl font-semibold text-mw-primary-600 dark:text-mw-primary-300 transition-transform duration-300 group-hover:scale-110">
                          {initials || <User className="w-6 h-6" />}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* Read-only fields */}
                <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                  {[
                    {
                      label: t("profile.personalInfo.fullName"),
                      value: fullName,
                    },
                    {
                      label: t("profile.personalInfo.emailAddress"),
                      value: user?.email ?? "—",
                    },
                    {
                      label: t("profile.personalInfo.phoneNumber"),
                      value: user?.phone || "—",
                    },
                    {
                      label: t("profile.personalInfo.role"),
                      value: user?.current_company?.role_name ?? "—",
                    },
                    {
                      label: t("profile.personalInfo.company"),
                      value: companyName || "—",
                    },
                  ].map(({ label, value }) => (
                    <div key={label} className="space-y-1">
                      <p className="text-xs font-medium text-mw-neutral-400 dark:text-mw-neutral-500 uppercase tracking-wide">
                        {label}
                      </p>
                      <p className="text-sm text-mw-neutral-800 dark:text-mw-neutral-100 font-medium">
                        {value}
                      </p>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="account" className="mt-4">
            <Card>
              <CardHeader className="p-6 pb-4 border-b border-container-border">
                <CardTitle>
                  <div className="flex items-center gap-2">
                    <Shield className="w-5 h-5 text-mw-neutral-500" />
                    <span className="text-lg font-semibold">
                      {t("profile.tabs.accountSecurity")}
                    </span>
                  </div>
                </CardTitle>
                <CardDescription>
                  <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 mt-1">
                    {t("profile.tabs.accountSecurityDesc")}
                  </p>
                </CardDescription>
              </CardHeader>
              <CardContent className="p-6 pt-6 space-y-5">
                <div className="space-y-2">
                  <p className="text-sm font-semibold text-mw-neutral-700 dark:text-mw-neutral-200">
                    {t("profile.tabs.accountStatus")}
                  </p>
                  <div className="flex items-center gap-2">
                    <Badge variant="success" size="sm">
                      {t("profile.tabs.active")}
                    </Badge>
                    <span className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
                      {t("profile.tabs.accountCreatedOn")}{" "}
                      {user?.updatedAt
                        ? new Date(user.updatedAt).toLocaleDateString()
                        : t("labels.na")}
                    </span>
                  </div>
                </div>

                <hr className="border-container-border dark:border-mw-neutral-700" />

                <div className="space-y-2">
                  <p className="text-sm font-semibold text-mw-neutral-700 dark:text-mw-neutral-200">
                    {t("profile.tabs.password")}
                  </p>
                  {/* <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400">
                    {t("profile.tabs.passwordLastChanged")}{" "}
                    <span className="font-medium text-mw-neutral-700 dark:text-mw-neutral-200">
                      {t("labels.na")}
                    </span>
                  </p> */}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setPasswordModalOpen(true)}
                    className="group"
                  >
                    <KeyRound className="w-3.5 h-3.5 mr-1.5 group-hover:animate-key-turn" />
                    {t("profile.tabs.changePassword")}
                  </Button>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      <ModalDrawer
        isOpen={passwordModalOpen}
        onClose={closePasswordModal}
        title={
          <span className="flex items-center gap-2">
            <KeyRound className="w-5 h-5 text-mw-primary-500" />
            {t("profile.tabs.changePassword")}
          </span>
        }
        size="md"
        showBackButton={false}
        id="change-password-drawer"
        footer={
          <Button
            type="button"
            variant="primary"
            size="lg"
            disabled={isPwdSubmitting}
            onClick={handleSubmitPwd(onChangePassword)}
            className="w-full justify-center"
          >
            {isPwdSubmitting
              ? t("messages.loading")
              : t("profile.tabs.updatePassword")}
          </Button>
        }
      >
        <form
          onSubmit={handleSubmitPwd(onChangePassword)}
          className="space-y-5"
        >
          <p className="text-sm text-mw-neutral-500 dark:text-mw-neutral-400 -mt-1">
            {t("profile.changePassword.description")}
          </p>

          {/* New password */}
          <div className="space-y-1">
            <label className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200">
              {t("profile.tabs.newPassword")}
            </label>
            <div className="relative">
              <input
                type={showNewPassword ? "text" : "password"}
                placeholder={t("profile.changePassword.newPasswordPlaceholder")}
                className="block w-full h-10 px-3 py-2 border rounded-md text-sm border-mw-neutral-200 dark:border-mw-neutral-600 text-mw-neutral-700 dark:text-mw-neutral-200 focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800 pr-10"
                {...registerPwd("newPassword")}
              />
              <button
                type="button"
                onClick={() => setShowNewPassword((p) => !p)}
                className="absolute right-3 top-3 text-mw-neutral-400 hover:text-mw-neutral-600 transition-colors"
              >
                {showNewPassword ? (
                  <EyeOff className="w-4 h-4" />
                ) : (
                  <Eye className="w-4 h-4" />
                )}
              </button>
            </div>
            {pwdErrors.newPassword && (
              <p className="text-xs text-red-500">
                {pwdErrors.newPassword.message}
              </p>
            )}
          </div>

          {/* Confirm password */}
          <div className="space-y-1">
            <label className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200">
              {t("profile.tabs.confirmPassword")}
            </label>
            <div className="relative">
              <input
                type={showConfirmPassword ? "text" : "password"}
                placeholder={t(
                  "profile.changePassword.confirmPasswordPlaceholder",
                )}
                className="block w-full h-10 px-3 py-2 border rounded-md text-sm border-mw-neutral-200 dark:border-mw-neutral-600 text-mw-neutral-700 dark:text-mw-neutral-200 focus:outline-none focus:ring-1 focus:ring-mw-primary-500 dark:bg-mw-neutral-800 pr-10"
                {...registerPwd("confirmPassword")}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword((p) => !p)}
                className="absolute right-3 top-3 text-mw-neutral-400 hover:text-mw-neutral-600 transition-colors"
              >
                {showConfirmPassword ? (
                  <EyeOff className="w-4 h-4" />
                ) : (
                  <Eye className="w-4 h-4" />
                )}
              </button>
            </div>
            {pwdErrors.confirmPassword && (
              <p className="text-xs text-red-500">
                {pwdErrors.confirmPassword.message}
              </p>
            )}
          </div>

          {/* Requirements box */}
          <div className="rounded-lg border border-mw-neutral-200 dark:border-mw-neutral-700 p-4 space-y-2">
            <p className="text-sm font-medium text-mw-neutral-700 dark:text-mw-neutral-200 mb-3">
              {t("profile.changePassword.requirements")}
            </p>
            {[
              {
                label: t("profile.changePassword.req.length"),
                met: pwdChecks.length,
              },
              {
                label: t("profile.changePassword.req.uppercase"),
                met: pwdChecks.upper,
              },
              {
                label: t("profile.changePassword.req.lowercase"),
                met: pwdChecks.lower,
              },
              {
                label: t("profile.changePassword.req.number"),
                met: pwdChecks.number,
              },
              {
                label: t("profile.changePassword.req.match"),
                met: passwordsMatch,
              },
            ].map(({ label, met }) => (
              <div key={label} className="flex items-center gap-2">
                {met ? (
                  <Check className="w-4 h-4 text-green-500 flex-shrink-0" />
                ) : (
                  <X className="w-4 h-4 text-mw-neutral-300 dark:text-mw-neutral-600 flex-shrink-0" />
                )}
                <span
                  className={`text-sm ${met ? "text-green-600 dark:text-green-400" : "text-mw-neutral-500 dark:text-mw-neutral-400"}`}
                >
                  {label}
                </span>
              </div>
            ))}

            {/* Strength bar */}
            {watchedNewPassword.length > 0 && (
              <div className="pt-2 space-y-1">
                <div className="flex gap-1">
                  {[1, 2, 3, 4].map((i) => (
                    <div
                      key={i}
                      className={`h-1 flex-1 rounded-full transition-all duration-300 ${
                        i <= pwdStrength
                          ? strengthColor
                          : "bg-mw-neutral-200 dark:bg-mw-neutral-700"
                      }`}
                    />
                  ))}
                </div>
                <p
                  className={`text-xs font-medium ${
                    pwdStrength <= 1
                      ? "text-red-500"
                      : pwdStrength === 2
                        ? "text-orange-500"
                        : pwdStrength === 3
                          ? "text-yellow-600"
                          : "text-green-600"
                  }`}
                >
                  {strengthLabel}
                </p>
              </div>
            )}
          </div>
        </form>
      </ModalDrawer>
    </div>
  );
};

export default ProfilePage;
