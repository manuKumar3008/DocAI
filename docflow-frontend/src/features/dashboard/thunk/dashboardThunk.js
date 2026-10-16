import { createAsyncThunk } from "@reduxjs/toolkit";

import dashboardService from "../services/dashboardService";

export const fetchDocuments = createAsyncThunk(
  "dashboard/fetchDocuments",

  async (params, thunkAPI) => {
    try {
      return await dashboardService.getDocuments(params);
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ??
          "Unable to load documents."
      );
    }
  }
);