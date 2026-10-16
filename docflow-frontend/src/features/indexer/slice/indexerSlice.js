import { createSlice } from "@reduxjs/toolkit";

import {
  fetchQueue,
  fetchDocumentTypes,
  submitReview,
} from "../thunk/indexerThunk";

const initialState = {
  queue: [],

  documentTypes: [],

  loading: false,

  error: null,

  // Pagination
  page: 0,          // MUI uses 0-based page index
  size: 10,
  total: 0,

  selectedDocument: null,

  dialog: {
    open: false,
    mode: "review",
  },
};

const indexerSlice = createSlice({
  name: "indexer",

  initialState,

  reducers: {
    openDialog(state, action) {
      state.dialog.open = true;
      state.selectedDocument = action.payload;
    },

    closeDialog(state) {
      state.dialog.open = false;
      state.selectedDocument = null;
    },
  },

  extraReducers: (builder) => {
    builder

      .addCase(fetchQueue.pending, (state) => {
        state.loading = true;
      })

      .addCase(fetchQueue.fulfilled, (state, action) => {
        state.loading = false;

        state.queue = action.payload.items;

        // Server-side pagination
        state.page = Math.max(
          0,
          (action.payload.page ?? 1) - 1
        );

        state.size =
          action.payload.size ??
          action.payload.limit ??
          10;

        state.total =
          action.payload.total ??
          action.payload.totalElements ??
          0;
      })

      .addCase(fetchQueue.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })

      .addCase(fetchDocumentTypes.fulfilled, (state, action) => {
        state.documentTypes = action.payload;
      })

      .addCase(submitReview.fulfilled, (state, action) => {
        state.queue = state.queue.filter(
          (document) =>
            document.documentId !== action.payload
        );

        state.dialog.open = false;
        state.selectedDocument = null;

        // Reduce total after successful review
        if (state.total > 0) {
          state.total -= 1;
        }
      });
  },
});

export const {
  openDialog,
  closeDialog,
} = indexerSlice.actions;

export default indexerSlice.reducer;