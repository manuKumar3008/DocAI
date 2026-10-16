const { Document } = require("../models");
const { createError } = require("../middleware/error.middleware");
const javaApiClient = require("./javaApi.client");

// ─── Parse SSE response from Java ──────────────────────────
const parseSSEResponse = (rawResponse) => {
  if (!rawResponse) return "";

  const lines = rawResponse.split("\n");
  const contentLines = [];

  for (const line of lines) {
    if (line.startsWith("data:")) {
      const content = line.replace(/^data:\s*/, "").trim();

      if (
        !content ||
        content === "[DONE]" ||
        content === "connected" ||
        content === "Connection closed" ||
        content.startsWith("Connected:") ||
        content.startsWith("Connection:")
      ) {
        continue;
      }

      contentLines.push(content);
    }
  }

  return contentLines.join("").trim();
};

// ─── Validate response is meaningful ───────────────────────
const isValidResponse = (text) => {
  if (!text || text.length < 10) return false;

  const failurePatterns = [
    "i don't have enough information",
    "i cannot answer",
    "no information available",
    "unable to answer",
    "not enough context",
  ];

  const lower = text.toLowerCase();
  return !failurePatterns.some((pattern) => lower.includes(pattern));
};

// ─── Validate an array of {pageNo, translation} pages ──────
const isValidPageResponse = (pages) => {
  if (!Array.isArray(pages) || pages.length === 0) return false;
  return pages.every((p) => isValidResponse(p?.translation));
};

// ─── Summarize Document ────────────────────────────────────
const summarizeDocument = async (
  documentId,
  { length = "SHORT", language = "GERMAN", forceRefresh = false } = {},
) => {
  const doc = await Document.findOne({ documentId });
  if (!doc) throw createError("Document not found", 404, "NOT_FOUND");

  const allowedStates = ["indexed"];
  if (!allowedStates.includes(doc.state)) {
    throw createError(
      "Document must be indexed before summarization",
      400,
      "DOCUMENT_NOT_INDEXED",
    );
  }

  if (doc.summary && !forceRefresh) {
    return {
      documentId,
      summary: doc.summary,
      cached: true,
    };
  }

  const type = String(length || "SHORT").toUpperCase();
  const targetLanguage = String(language || "GERMAN").toUpperCase();
  const storagePath = doc.storagePath || "";

  const javaResult = await javaApiClient.summarizeDocumentInJava(
    documentId,
    type,
    targetLanguage,
    storagePath,
  );

  if (!javaResult.success) {
    throw createError(
      "Summarization failed. Please try again.",
      500,
      "SUMMARIZATION_FAILED",
    );
  }

  const summary = javaResult.data?.summary;
  if (!summary || !isValidResponse(summary)) {
    throw createError(
      "Could not generate a valid summary.",
      500,
      "SUMMARIZATION_FAILED",
    );
  }

  await Document.findOneAndUpdate({ documentId }, { summary });

  return {
    documentId,
    summary,
    cached: false,
  };
};

// ─── Translate Document ────────────────────────────────────
// options.pages accepts a single page ("5"), a range ("2-3"), or is
// omitted to translate the whole document, page by page (0-based).
// Always resolves translatedPages as an array of { pageNo, translation }.
const translateDocument = async (
  documentId,
  { targetLanguage, pages = null, forceRefresh = false } = {},
) => {
  if (!targetLanguage) {
    throw createError("targetLanguage is required", 400, "VALIDATION_ERROR");
  }

  const doc = await Document.findOne({ documentId });
  if (!doc) throw createError("Document not found", 404, "NOT_FOUND");

  const allowedStates = ["indexed", "in_progress", "completed"];
  if (!allowedStates.includes(doc.state)) {
    throw createError(
      "Document must be indexed before translation",
      400,
      "DOCUMENT_NOT_INDEXED",
    );
  }

  if (
    doc.translation?.pages?.length &&
    doc.translation?.targetLanguage === targetLanguage &&
    !forceRefresh
  ) {
    return {
      documentId,
      language: targetLanguage,
      translatedPages: doc.translation.pages,
      cached: true,
    };
  }

  const relativeStoragePath = doc.storagePath;

  const javaResult = await javaApiClient.translateDocumentInJava(
    documentId,
    targetLanguage,
    relativeStoragePath,
    pages,
  );

  if (!javaResult.success) {
    throw createError(
      "Translation failed. Please try again.",
      500,
      "TRANSLATION_FAILED",
    );
  }

  const translatedPages = javaResult.data || [];

  if (!isValidPageResponse(translatedPages)) {
    throw createError(
      "Could not generate a valid translation. Ensure document is properly indexed.",
      500,
      "TRANSLATION_FAILED",
    );
  }

  await Document.findOneAndUpdate(
    { documentId },
    {
      translation: {
        targetLanguage,
        pages: translatedPages,
      },
    },
  );

  return {
    documentId,
    language: targetLanguage,
    translatedPages,
    cached: false,
  };
};

// ─── Translate Document — streaming ────────────────────────
// Same validation and page-selection semantics as translateDocument, but
// writes each {pageNo, translation} to nodeRes as SSE events as soon as
// that page is ready. Only caches the result when translating the whole
// document (no pages filter) — a partial-range stream won't overwrite a
// previously cached full translation.
const streamTranslateDocument = async (
  documentId,
  { targetLanguage, pages = null, forceRefresh = false } = {},
  nodeRes,
) => {
  if (!targetLanguage) {
    throw createError("targetLanguage is required", 400, "VALIDATION_ERROR");
  }

  const doc = await Document.findOne({ documentId });
  if (!doc) throw createError("Document not found", 404, "NOT_FOUND");

  const allowedStates = ["indexed", "in_progress", "completed"];
  if (!allowedStates.includes(doc.state)) {
    throw createError(
      "Document must be indexed before translation",
      400,
      "DOCUMENT_NOT_INDEXED",
    );
  }

  // Serve straight from cache — as a stream of already-translated pages —
  // when translating the whole document and a cached translation exists.
  if (
    !pages &&
    doc.translation?.pages?.length &&
    doc.translation?.targetLanguage === targetLanguage &&
    !forceRefresh
  ) {
    for (const page of doc.translation.pages) {
      nodeRes.write(`data: ${JSON.stringify(page)}\n\n`);
    }
    nodeRes.write(`data: [DONE]\n\n`);
    nodeRes.end();
    return;
  }

  const relativeStoragePath = doc.storagePath;
  const collectedPages = [];

  await javaApiClient.streamTranslateDocumentInJava(
    documentId,
    targetLanguage,
    relativeStoragePath,
    pages,
    nodeRes,
    (pageObj) => {
      collectedPages.push(pageObj);
    },
  );

  if (!pages && isValidPageResponse(collectedPages)) {
    await Document.findOneAndUpdate(
      { documentId },
      {
        translation: {
          targetLanguage,
          pages: collectedPages,
        },
      },
    );
  }
};

module.exports = {
  summarizeDocument,
  translateDocument,
  streamTranslateDocument,
};
