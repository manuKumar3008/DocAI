import { createSlice } from "@reduxjs/toolkit";

import { fetchIndexedDocuments } from "../thunk/documentLibraryThunk";

const initialState = {
  documents: [],

  selectedDocuments: [],

  total: 0,
  page: 1,
  size: 10,
  totalPages: 0,

  loading: false,

  error: null,

  search: "",
};

const documentLibrarySlice = createSlice({
  name: "documentLibrary",

  initialState,

  reducers: {
    setSearch(state, action) {
      state.search = action.payload;
    },

    toggleDocumentSelection(state, action) {
      const document = action.payload;

      const exists = state.selectedDocuments.find(
        (item) =>
          item.documentId === document.documentId
      );

      if (exists) {
        state.selectedDocuments =
          state.selectedDocuments.filter(
            (item) =>
              item.documentId !==
              document.documentId
          );
      } else {
        state.selectedDocuments.push(document);
      }
    },

    clearSelection(state) {
      state.selectedDocuments = [];
    },

    selectAllDocuments(state, action) {
      state.selectedDocuments = action.payload;
    },
  },

  extraReducers: (builder) => {
    builder
      .addCase(fetchIndexedDocuments.pending, (state) => {
        state.loading = true;
        state.error = null;
      })

      .addCase(fetchIndexedDocuments.fulfilled, (state, action) => {
        state.loading = false;

        state.documents = action.payload.items;

        // Pagination metadata
        state.total = action.payload.total;
        state.page = action.payload.page;
        state.size = action.payload.size;
        state.totalPages = action.payload.totalPages;
      })

      .addCase(fetchIndexedDocuments.rejected, (state, action) => {
        state.loading = false;

        state.error = action.payload;
      });
  },
});

export const {
  setSearch,
  toggleDocumentSelection,
  clearSelection,
  selectAllDocuments,
} = documentLibrarySlice.actions;

export default documentLibrarySlice.reducer;