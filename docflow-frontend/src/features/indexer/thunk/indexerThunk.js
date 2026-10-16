import { createAsyncThunk } from "@reduxjs/toolkit";

import indexerService from "../service/indexerService";

export const fetchQueue = createAsyncThunk(
  "indexer/fetchQueue",

  async (
    {
      page = 1,
      size = 10,
    } = {},
    { rejectWithValue }
  ) => {
    try {
      const response =
        await indexerService.getQueue({
          page,
          size,
        });

      return response.data.data;
    } catch (error) {
      return rejectWithValue(
        error.response?.data?.message ||
          "Unable to fetch queue."
      );
    }
  }
);

export const fetchDocumentTypes =
  createAsyncThunk(
    "indexer/fetchDocumentTypes",

    async (
      _,
      { rejectWithValue }
    ) => {
      try {
        const response =
          await indexerService.getDocumentTypes();

        return response.data.data.items;
      } catch (error) {
        return rejectWithValue(
          error.response?.data?.message ||
            "Unable to fetch document types."
        );
      }
    }
  );

export const submitReview =
  createAsyncThunk(
    "indexer/submitReview",

    async (
      {
        documentId,
        payload,
      },
      { rejectWithValue }
    ) => {
      try {
        await indexerService.submitReview(
          documentId,
          payload
        );

        return documentId;
      } catch (error) {
        return rejectWithValue(
          error.response?.data?.message ||
            "Review submission failed."
        );
      }
    }
  );