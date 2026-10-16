import { createSlice } from "@reduxjs/toolkit";

import {
  loadDocumentTypes,
  createDocumentTypeThunk,
  updateDocumentTypeThunk,
  toggleDocumentTypeStatusThunk,
  deleteDocumentTypeThunk,
  loadConfidenceThreshold,
  updateConfidenceThresholdThunk,
} from "../thunk/administrationThunk";

const initialState = {
  // ==========================================================
  // Document Types
  // ==========================================================

  documentTypes: [],
  total: 0,

  // MUI uses 0-based page indexing
  page: 0,
  size: 10,
  totalPages: 0,
  search: "",

  // ==========================================================
  // AI Configuration
  // ==========================================================

  confidenceThreshold: null,

  quickActions: {
    summary: true,
    translation: true,
    metadata: true,
  },

  // ==========================================================
  // Loading States
  // ==========================================================

  loadingDocumentTypes: false,
  loadingThreshold: false,
  savingDocumentType: false,
  savingThreshold: false,

  // ==========================================================
  // Error
  // ==========================================================

  error: null,
};

const administrationSlice = createSlice({
  name: "administration",
  initialState,

  reducers: {
    setSearch(state, action) {
      state.search = action.payload;
    },

    setPage(state, action) {
      state.page = action.payload;
    },

    setSize(state, action) {
      state.size = action.payload;
    },

    // ==========================================================
    // AI Quick Actions
    // ==========================================================

    setQuickActions(state, action) {
      state.quickActions = action.payload;
    },

    toggleQuickAction(state, action) {
      const feature = action.payload;

      state.quickActions[feature] =
        !state.quickActions[feature];
    },

    clearAdministrationError(state) {
      state.error = null;
    },
  },

  extraReducers: (builder) => {
    builder

      // ==========================================================
      // Load Document Types
      // ==========================================================

      .addCase(loadDocumentTypes.pending, (state) => {
        state.loadingDocumentTypes = true;
        state.error = null;
      })

      .addCase(loadDocumentTypes.fulfilled, (state, action) => {
        state.loadingDocumentTypes = false;

        const data = action.payload?.data ?? {};

        state.documentTypes = data.items ?? [];
        state.total = data.total ?? 0;

        // Backend page starts at 1, MUI starts at 0
        state.page = Math.max(
          0,
          (data.page ?? 1) - 1
        );

        state.size = data.limit ?? 10;
        state.totalPages = data.totalPages ?? 0;
      })

      .addCase(loadDocumentTypes.rejected, (state, action) => {
        state.loadingDocumentTypes = false;
        state.error = action.payload;
      })

      // ==========================================================
      // Create Document Type
      // ==========================================================

      .addCase(createDocumentTypeThunk.pending, (state) => {
        state.savingDocumentType = true;
        state.error = null;
      })

      .addCase(createDocumentTypeThunk.fulfilled, (state) => {
        state.savingDocumentType = false;
      })

      .addCase(createDocumentTypeThunk.rejected, (state, action) => {
        state.savingDocumentType = false;
        state.error = action.payload;
      })

      // ==========================================================
      // Update Document Type
      // ==========================================================

      .addCase(updateDocumentTypeThunk.pending, (state) => {
        state.savingDocumentType = true;
        state.error = null;
      })

      .addCase(updateDocumentTypeThunk.fulfilled, (state) => {
        state.savingDocumentType = false;
      })

      .addCase(updateDocumentTypeThunk.rejected, (state, action) => {
        state.savingDocumentType = false;
        state.error = action.payload;
      })

      // ==========================================================
      // Toggle Status
      // ==========================================================

      .addCase(toggleDocumentTypeStatusThunk.pending, (state) => {
        state.savingDocumentType = true;
        state.error = null;
      })

      .addCase(toggleDocumentTypeStatusThunk.fulfilled, (state) => {
        state.savingDocumentType = false;
      })

      .addCase(toggleDocumentTypeStatusThunk.rejected, (state, action) => {
        state.savingDocumentType = false;
        state.error = action.payload;
      })

      // ==========================================================
      // Delete Document Type
      // ==========================================================

      .addCase(deleteDocumentTypeThunk.pending, (state) => {
        state.savingDocumentType = true;
        state.error = null;
      })

      .addCase(deleteDocumentTypeThunk.fulfilled, (state) => {
        state.savingDocumentType = false;
      })

      .addCase(deleteDocumentTypeThunk.rejected, (state, action) => {
        state.savingDocumentType = false;
        state.error = action.payload;
      })

      // ==========================================================
      // Load Confidence Threshold
      // ==========================================================

      .addCase(loadConfidenceThreshold.pending, (state) => {
        state.loadingThreshold = true;
        state.error = null;
      })

      .addCase(loadConfidenceThreshold.fulfilled, (state, action) => {
        state.loadingThreshold = false;

        state.confidenceThreshold =
          action.payload?.data?.threshold ?? null;
      })

      .addCase(loadConfidenceThreshold.rejected, (state, action) => {
        state.loadingThreshold = false;
        state.error = action.payload;
      })

      // ==========================================================
      // Update Confidence Threshold
      // ==========================================================

      .addCase(updateConfidenceThresholdThunk.pending, (state) => {
        state.savingThreshold = true;
        state.error = null;
      })

      .addCase(updateConfidenceThresholdThunk.fulfilled, (state, action) => {
        state.savingThreshold = false;

        state.confidenceThreshold =
          action.payload?.data?.threshold ??
          state.confidenceThreshold;
      })

      .addCase(updateConfidenceThresholdThunk.rejected, (state, action) => {
        state.savingThreshold = false;
        state.error = action.payload;
      });
  },
});

export const {
  setSearch,
  setPage,
  setSize,
  setQuickActions,
  toggleQuickAction,
  clearAdministrationError,
} = administrationSlice.actions;

export default administrationSlice.reducer;