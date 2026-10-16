import { nanoid } from "@reduxjs/toolkit";

import {
  addMessage,
  updateMessage,
} from "../slice/conversationSlice";

import { summarizeDocument } from "../services/conversationApi";

export const summarizeThunk =
  (documentId, options = {}) =>
  async (dispatch) => {
    const {
      language = "ENGLISH",
      length = "SHORT",
      forceRefresh = true,
    } = options;

    const userMessage = {
      id: nanoid(),
      role: "USER",
      type: "SUMMARY",
      content: `Generate a ${length.toLowerCase()} summary in ${language}.`,
      createdAt: new Date().toISOString(),
    };

    dispatch(addMessage(userMessage));

    const aiMessageId = nanoid();

    dispatch(
      addMessage({
        id: aiMessageId,
        role: "ASSISTANT",
        type: "SUMMARY",
        content: "",
        loading: true,
        createdAt: new Date().toISOString(),
      })
    );

    try {
      const response = await summarizeDocument(documentId, {
        length,
        language,
        forceRefresh,
      });

      if (!response.success) {
        throw new Error("Summary generation failed");
      }

      dispatch(
        updateMessage({
          id: aiMessageId,
          content: response.data.summary,
          metadata: {
            cached: response.data.cached,
          },
        })
      );
    } catch (error) {
      //console.error("Summary API Error:", error);

      dispatch(
        updateMessage({
          id: aiMessageId,
          content:
            "Sorry, I couldn't generate the summary. Please try again.",
          metadata: {
            cached: false,
          },
        })
      );
    }
  };