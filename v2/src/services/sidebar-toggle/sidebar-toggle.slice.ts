import { createSlice } from "@reduxjs/toolkit";

export interface SideBar {
  isSidebarCollapsed: boolean;
}

const initialState: SideBar = {
  isSidebarCollapsed: false,
};

const sidebarSlice = createSlice({
  name: "sidebar",
  initialState,
  reducers: {
    toggleSidebar(state) {
      state.isSidebarCollapsed = !state.isSidebarCollapsed;
    },
    setSidebarCollapsed(state, action: { payload: boolean }) {
      state.isSidebarCollapsed = action.payload;
    },
  },
});

export const { toggleSidebar, setSidebarCollapsed } = sidebarSlice.actions;
export default sidebarSlice.reducer;
