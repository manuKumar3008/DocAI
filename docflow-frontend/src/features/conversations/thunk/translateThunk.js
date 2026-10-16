import { nanoid } from "@reduxjs/toolkit";

import {
  addMessage,
  updateMessage,
} from "../slice/conversationSlice";

import { translationStream } from "../services/translationStream";

/**
 * Convert page selection into an array.
 *
 * Examples:
 * pageCount=5, ""          -> [1,2,3,4,5]
 * "1-3"                    -> [1,2,3]
 * "2,5,8"                  -> [2,5,8]
 * "2-4,7,10-11"            -> [2,3,4,7,10,11]
 */
const buildRequestedPages = (
  pageCount,
  pageSelection
) => {
  // If no custom pages are provided, translate all pages.
  if (!pageSelection || pageSelection.trim() === "") {
    return Array.from(
      { length: pageCount },
      (_, index) => index + 1
    );
  }

  const pages = new Set();

  pageSelection
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean)
    .forEach((item) => {
      if (item.includes("-")) {
        let [start, end] = item
          .split("-")
          .map((value) => Number(value));

        if (Number.isNaN(start) || Number.isNaN(end)) {
          return;
        }

        // Handle reversed ranges like 5-2
        if (start > end) {
          [start, end] = [end, start];
        }

        // Clamp to valid document page range
        start = Math.max(1, start);
        end = Math.min(pageCount, end);

        for (let page = start; page <= end; page++) {
          pages.add(page);
        }
      } else {
        const page = Number(item);

        if (
          !Number.isNaN(page) &&
          page >= 1 &&
          page <= pageCount
        ) {
          pages.add(page);
        }
      }
    });

  return [...pages].sort((a, b) => a - b);
};

export const translateThunk =
  (documentId, payload) =>
    async (dispatch, getState) => {
      //console.log("translateThunk started");
      const {
        targetLanguage,
        pages,
        forceRefresh = true,
        pageCount = 0,
      } = payload;

      const hasCustomPages =
        typeof pages === "string" &&
        pages.trim() !== "";

      const normalizedPages = hasCustomPages
        ? pages.trim()
        : "";

      const requestedPages = buildRequestedPages(
        pageCount,
        normalizedPages
      );

      const userMessage = hasCustomPages
        ? `Translate pages ${normalizedPages} of this document to ${targetLanguage}`
        : `Translate this document to ${targetLanguage}`;

      dispatch(
        addMessage({
          id: nanoid(),
          role: "USER",
          type: "TRANSLATION",
          content: userMessage,
          createdAt: new Date().toISOString(),
        })
      );

      const translationMessageId = nanoid();

      dispatch(
        addMessage({
          id: translationMessageId,
          role: "ASSISTANT",
          type: "TRANSLATION",
          loading: true,
          content: "",
          createdAt: new Date().toISOString(),
          metadata: {
            language: targetLanguage,
            totalPages: pageCount,
            requestedPages,
            streamed: true,
            status: "IN_PROGRESS",
            lastTranslatedPage: null,
            translatedPages: {},
          },
        })
      );

      const state = getState();

      const token =
        state.auth?.token ||
        state.auth?.accessToken ||
        state.user?.token;

      const requestBody = {
        targetLanguage,
        forceRefresh,
      };

      if (hasCustomPages) {
        requestBody.pages = normalizedPages;
      }

      try {
        //console.log("Calling translation stream...");
        await translationStream({
          documentId,
          payload: requestBody,
          token,

          onChunk: ({ pageNo, translation }) => {
            if (!translation) return;

            //console.log("================================");
            //console.log("Backend pageNo:", pageNo);
            //console.log("hasCustomPages:", hasCustomPages);
            //console.log("requestedPages:", requestedPages);
            //console.log("requestedPages.length:", requestedPages.length);
            //console.log("requestedPages[pageNo]:", requestedPages[Number(pageNo)]);

            // Backend is 0-based -> UI is 1-based
            const uiPageNo = Number(pageNo);

            dispatch(
              updateMessage({
                id: translationMessageId,
                loading: false,
                metadata: {
                  lastTranslatedPage: uiPageNo,
                  translatedPages: {
                    [uiPageNo]: translation,
                  },
                },
              })
            );
          },

          onDone: () => {
            dispatch(
              updateMessage({
                id: translationMessageId,
                loading: false,
                metadata: {
                  status: "COMPLETED",
                },
              })
            );

            //console.log("Translation completed");
          },

          onError: (error) => {
            throw error;
          },
        });
      } catch (error) {
        //console.error(error);

        dispatch(
          updateMessage({
            id: translationMessageId,
            loading: false,
            content: "Unable to translate document.",
            metadata: {
              status: "FAILED",
            },
          })
        );
      }
    };