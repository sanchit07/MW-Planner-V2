import { configureStore } from "@reduxjs/toolkit";
import { setupListeners } from "@reduxjs/toolkit/query";
import { accountApi, accountUserApi } from "@services/account/accountApi";
import { agencyApi } from "@services/agency/agencySlice";
import { authApi } from "@services/auth/authSlice";
import authSlice from "@services/auth/authSlice";
import { brandApi, iamBrandApi } from "@services/brand/brandSlice";
import brandSlice from "@services/brand/brandSlice";
import { campaignDetailsApi } from "@services/campaign/campaignDetailsSlice";
import campaignDetailsSlice from "@services/campaign/campaignDetailsSlice";
import { campaignApi, companyApi } from "@services/campaign/campaignSlice";
import campaignSlice from "@services/campaign/campaignSlice";
import campaignsUISlice from "@services/campaign/campaignsUISlice";
import { companyBrandingApi } from "@services/companyBranding/companyBrandingSlice";
import {
  configurationMetadataReducer,
  configurationMetadataAPI,
} from "@services/configuration-metadata/configurationMetadataSlice";
import { creativeApi } from "@services/creative/creativeSlice";
import { dashboardApi } from "@services/dashboard/dashboardSlice";
import { intercomApi } from "@services/intercom/intercomApi";
import {
  inventoryApi,
  inventoryManagementApi,
  reachFrequencyApi,
} from "@services/inventory/inventorySlice";
import mapMarkerLocationsSlice from "@services/map-marker-lists/mapMarkerLocationsSlice";
import { plannerConfigurationApi } from "@services/plannerConfiguration/plannerConfigurationSlice";
import {
  publicAccessApi,
  publicInventoryApi,
} from "@services/public-access/publicAccessSlice";
import { reservationApi } from "@services/reservation/reservationSlice";
import sidebarSlice from "@services/sidebar-toggle/sidebar-toggle.slice";
import stepperSlice from "@services/stepper/stepperSlice";
import { statementApi } from "@services/statement/statementSlice";
import { testModeApi } from "@services/testMode/testModeSlice";
import { userApi } from "@services/user/userSlice";
import userSlice from "@services/user/userSlice";
import { TypedUseSelectorHook, useDispatch, useSelector } from "react-redux";

export const store = configureStore({
  reducer: {
    sidebar: sidebarSlice,
    auth: authSlice,
    profile: userSlice,
    campaign: campaignSlice,
    campaignDetails: campaignDetailsSlice,
    brand: brandSlice,
    stepper: stepperSlice,
    configurationMetadata: configurationMetadataReducer,
    configurationMetadataAPI: configurationMetadataAPI.reducer,
    mapMarkerLocations: mapMarkerLocationsSlice,
    campaignsUI: campaignsUISlice,
    [authApi.reducerPath]: authApi.reducer,
    [userApi.reducerPath]: userApi.reducer,
    [campaignApi.reducerPath]: campaignApi.reducer,
    [companyApi.reducerPath]: companyApi.reducer,
    [campaignDetailsApi.reducerPath]: campaignDetailsApi.reducer,
    [brandApi.reducerPath]: brandApi.reducer,
    [iamBrandApi.reducerPath]: iamBrandApi.reducer,
    [inventoryApi.reducerPath]: inventoryApi.reducer,
    [inventoryManagementApi.reducerPath]: inventoryManagementApi.reducer,
    [reachFrequencyApi.reducerPath]: reachFrequencyApi.reducer,
    [publicAccessApi.reducerPath]: publicAccessApi.reducer,
    [publicInventoryApi.reducerPath]: publicInventoryApi.reducer,
    [dashboardApi.reducerPath]: dashboardApi.reducer,
    [accountApi.reducerPath]: accountApi.reducer,
    [accountUserApi.reducerPath]: accountUserApi.reducer,
    [agencyApi.reducerPath]: agencyApi.reducer,
    [intercomApi.reducerPath]: intercomApi.reducer,
    [testModeApi.reducerPath]: testModeApi.reducer,
    [plannerConfigurationApi.reducerPath]: plannerConfigurationApi.reducer,
    [companyBrandingApi.reducerPath]: companyBrandingApi.reducer,
    [creativeApi.reducerPath]: creativeApi.reducer,
    [reservationApi.reducerPath]: reservationApi.reducer,
    [statementApi.reducerPath]: statementApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(
      authApi.middleware,
      userApi.middleware,
      campaignApi.middleware,
      companyApi.middleware,
      campaignDetailsApi.middleware,
      configurationMetadataAPI.middleware,
      brandApi.middleware,
      iamBrandApi.middleware,
      inventoryApi.middleware,
      inventoryManagementApi.middleware,
      reachFrequencyApi.middleware,
      publicAccessApi.middleware,
      publicInventoryApi.middleware,
      dashboardApi.middleware,
      accountApi.middleware,
      accountUserApi.middleware,
      agencyApi.middleware,
      intercomApi.middleware,
      testModeApi.middleware,
      plannerConfigurationApi.middleware,
      companyBrandingApi.middleware,
      creativeApi.middleware,
      reservationApi.middleware,
      statementApi.middleware,
    ),
});

setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// Pre-typed hooks for better TypeScript support
export const useAppDispatch: () => AppDispatch = useDispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
