// src/features/auth/slice/authSlice.js

import { createSlice } from "@reduxjs/toolkit";

import { login, logout } from "../thunk/authThunk";
import { tokenStorage } from "../../../utils/tokenStorage";

const storedToken = tokenStorage.getToken();
const storedUser = tokenStorage.getUser();

const initialState = {
  user: storedUser,
  token: storedToken,
  isAuthenticated: !!storedToken,
  loading: false,
  error: null,
};

const resetAuthState = (state) => {
  state.user = null;
  state.token = null;
  state.isAuthenticated = false;
  state.loading = false;
  state.error = null;
};

const authSlice = createSlice({
  name: "auth",

  initialState,

  reducers: {
    restoreSession(state, action) {
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.isAuthenticated = true;
      state.loading = false;
      state.error = null;
    },
  },

  extraReducers: (builder) => {
    builder

      // =========================
      // Login
      // =========================

      .addCase(login.pending, (state) => {
        state.loading = true;
        state.error = null;
      })

      .addCase(login.fulfilled, (state, action) => {
        state.loading = false;
        state.user = action.payload.user;
        state.token = action.payload.token;
        state.isAuthenticated = true;
        state.error = null;
      })

      .addCase(login.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
        state.isAuthenticated = false;
      })

      // =========================
      // Logout
      // =========================

      .addCase(logout.pending, (state) => {
        state.loading = true;
      })

      .addCase(logout.fulfilled, resetAuthState)

      .addCase(logout.rejected, resetAuthState);
  },
});

export const { restoreSession } = authSlice.actions;

export default authSlice.reducer;