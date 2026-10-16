import { createSlice } from "@reduxjs/toolkit";

import { fetchDocuments } from "../thunk/dashboardThunk";

const initialState = {
  documents: [],

  loading: false,

  error: null,

  page: 0,

  size: 10,

  total: 0,

  totalPages: 0,

  filters: {
    status: "",
    documentType: "",
  },
};

const dashboardSlice = createSlice({
  name: "dashboard",

  initialState,

  reducers: {
    setPage(state, action) {
      state.page = action.payload;
    },

    setRowsPerPage(state, action) {
      state.size = action.payload;
      state.page = 0;
    },

    setStatusFilter(state, action) {
      state.filters.status = action.payload;
      state.page = 0;
    },

    setDocumentTypeFilter(state, action) {
      state.filters.documentType = action.payload;
      state.page = 0;
    },
  },

  extraReducers: (builder) => {
    builder

      .addCase(fetchDocuments.pending, (state) => {
        state.loading = true;
        state.error = null;
      })

      .addCase(fetchDocuments.fulfilled, (state, action) => {
        state.loading = false;

        state.documents = action.payload.items;

        state.total = action.payload.total;

        state.page = action.payload.page - 1;

        state.size = action.payload.size;

        state.totalPages = action.payload.totalPages;
      })

      .addCase(fetchDocuments.rejected, (state, action) => {
        state.loading = false;

        state.error = action.payload;
      });
  },
});

export const {
  setPage,
  setRowsPerPage,
  setStatusFilter,
  setDocumentTypeFilter,
} = dashboardSlice.actions;

export default dashboardSlice.reducer;