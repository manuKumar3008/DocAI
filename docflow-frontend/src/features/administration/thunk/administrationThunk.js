import { createAsyncThunk } from "@reduxjs/toolkit";

import {
  getDocumentTypes,
  createDocumentType,
  updateDocumentType,
  deleteDocumentType,
  getConfidenceThreshold,
  updateConfidenceThreshold,
} from "../services/administrationApi";

/**
 * Load both Administration sections
 */
export const loadAdministration = createAsyncThunk(
  "administration/loadAdministration",
  async (_, { dispatch }) => {
    await Promise.all([
      dispatch(loadDocumentTypes()),
      dispatch(loadConfidenceThreshold()),
    ]);
  }
);

/**
 * Load Document Types
 */
export const loadDocumentTypes = createAsyncThunk(
  "administration/loadDocumentTypes",
  async (params = {}, { rejectWithValue }) => {
    try {
      return await getDocumentTypes(params);
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);

/**
 * Create Document Type
 */
export const createDocumentTypeThunk = createAsyncThunk(
  "administration/createDocumentType",
  async (payload, { dispatch, rejectWithValue }) => {
    try {
      const response = await createDocumentType(payload);

      await dispatch(loadDocumentTypes());

      return response;
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);

/**
 * Update Document Type
 */
export const updateDocumentTypeThunk = createAsyncThunk(
  "administration/updateDocumentType",
  async ({ documentType, payload }, { dispatch, rejectWithValue }) => {
    try {
      const response = await updateDocumentType(
        documentType,
        payload
      );

      await dispatch(loadDocumentTypes());

      return response;
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);

/**
 * Toggle Active / Inactive Status
 */
export const toggleDocumentTypeStatusThunk = createAsyncThunk(
  "administration/toggleDocumentTypeStatus",
  async ({ documentType, payload }, { dispatch, rejectWithValue }) => {
    try {
      const response = await updateDocumentType(
        documentType,
        payload
      );

      await dispatch(loadDocumentTypes());

      return response;
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);

/**
 * Delete Document Type
 */
export const deleteDocumentTypeThunk = createAsyncThunk(
  "administration/deleteDocumentType",
  async (documentType, { dispatch, rejectWithValue }) => {
    try {
      const response = await deleteDocumentType(documentType);

      await dispatch(loadDocumentTypes());

      return response;
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);

/**
 * Load Confidence Threshold
 */
export const loadConfidenceThreshold = createAsyncThunk(
  "administration/loadConfidenceThreshold",
  async (_, { rejectWithValue }) => {
    try {
      return await getConfidenceThreshold();
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);

/**
 * Update Confidence Threshold
 */
export const updateConfidenceThresholdThunk = createAsyncThunk(
  "administration/updateConfidenceThreshold",
  async (payload, { rejectWithValue }) => {
    try {
      return await updateConfidenceThreshold(payload);
    } catch (error) {
      return rejectWithValue(
        error.response?.data || error.message
      );
    }
  }
);