import { createAsyncThunk } from "@reduxjs/toolkit";

import authService from "../services/authService";
import { tokenStorage } from "../../../utils/tokenStorage";

/**
 * Login
 */
export const login = createAsyncThunk(
  "auth/login",
  async (credentials, thunkAPI) => {
    ////console.log("STEP 2 - Thunk started");
    try {
      const response = await authService.login(credentials);
      ////console.log("STEP 3 - API Success", response);

      // Persist JWT
      tokenStorage.setToken(response.token);
      tokenStorage.setUser(response.user);
      return response;
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ||
        "Unable to login."
      );
    }
  }
);

/**
 * Logout
 */
export const logout = createAsyncThunk(
  "auth/logout",
  async (_, thunkAPI) => {
    try {
      // Call backend logout API
      await authService.logout();
    } catch (error) {
      // Ignore backend failures and continue with local logout
      //console.error("Logout API failed:", error);
    } finally {
      // Always clear local session
      tokenStorage.clear();
    }

    return true;
  }
);