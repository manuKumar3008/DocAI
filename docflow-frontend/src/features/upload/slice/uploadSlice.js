// src/features/upload/slice/uploadSlice.js

import { createSlice } from "@reduxjs/toolkit";

import { uploadDocument } from "../thunk/uploadThunk";

const initialState = {
  uploading: false,

  uploadedDocument: null,

  error: null,
};

const uploadSlice = createSlice({
  name: "upload",

  initialState,

  reducers: {
    clearUpload(state) {
      state.uploadedDocument = null;
      state.error = null;
    },
  },

  extraReducers: (builder) => {
    builder

      .addCase(
        uploadDocument.pending,
        (state) => {
          state.uploading = true;
          state.error = null;
        }
      )

      .addCase(
        uploadDocument.fulfilled,
        (state, action) => {
          state.uploading = false;

          state.uploadedDocument =
            action.payload;
        }
      )

      .addCase(
        uploadDocument.rejected,
        (state, action) => {
          state.uploading = false;

          state.error =
            action.payload;
        }
      );
  },
});

export const {
  clearUpload,
} = uploadSlice.actions;

export default uploadSlice.reducer;