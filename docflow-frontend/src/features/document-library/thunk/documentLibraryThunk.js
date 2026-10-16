import { createAsyncThunk } from "@reduxjs/toolkit";

import documentLibraryService from "../services/documentLibraryService";

export const fetchIndexedDocuments =
  createAsyncThunk(
    "documentLibrary/fetchIndexedDocuments",

    async (
      params = {},
      { rejectWithValue }
    ) => {
      try {
        const response =
          await documentLibraryService.getIndexedDocuments(
            params
          );

        return response;
      } catch (error) {
        return rejectWithValue(
          error.response?.data?.message ||
            "Failed to fetch indexed documents."
        );
      }
    }
  );