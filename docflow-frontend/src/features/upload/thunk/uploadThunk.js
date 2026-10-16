// src/features/upload/thunk/uploadThunk.js

import { createAsyncThunk } from "@reduxjs/toolkit";

import uploadService from "../service/uploadService";

export const uploadDocument =
  createAsyncThunk(
    "upload/uploadDocument",

    async (
      {
        file,
        forceReindex,
      },
      { rejectWithValue }
    ) => {
      try {
        const formData =
          new FormData();

        formData.append(
          "file",
          file
        );

        formData.append(
          "forceReindex",
          forceReindex
        );

        const response =
          await uploadService.upload(
            formData
          );

        return response.data.data;
      } catch (error) {
        return rejectWithValue(
          error.response?.data?.message ||
            "Upload failed"
        );
      }
    }
  );