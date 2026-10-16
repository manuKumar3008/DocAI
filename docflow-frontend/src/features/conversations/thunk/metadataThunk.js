import { createAsyncThunk } from "@reduxjs/toolkit";
import { getDocumentMetadata } from "../services/conversationApi";

export const fetchDocumentMetadataThunk = createAsyncThunk(
    "conversation/fetchDocumentMetadata",
    async (documentId, { rejectWithValue }) => {
        try {
            const response = await getDocumentMetadata(documentId);

            //console.log("Metadata Response:", response);

            if (!response.success) {
                return rejectWithValue(
                    response.message || "Failed to fetch metadata."
                );
            }

            return response.data;
        } catch (error) {
            return rejectWithValue(
                error.response?.data?.message ||
                error.message ||
                "Failed to fetch metadata."
            );
        }
    }
);