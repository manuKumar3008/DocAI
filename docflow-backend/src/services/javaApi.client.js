const axios = require("axios");
const FormData = require("form-data");
const fs = require("fs");

const javaClient = axios.create({
  baseURL: process.env.JAVA_API_BASE_URL,
  timeout: 180000,
});

// ─── Upload to Java ─────────────────────────────────────────
const uploadToJava = async (
  filePath,
  fileName,
  documentId,
  forceReindex = false,
) => {
  try {
    const form = new FormData();
    form.append("file", fs.createReadStream(filePath), fileName);
    form.append("documentId", documentId);
    form.append("forceReindex", String(forceReindex));

    const response = await javaClient.post("/document-upload/documents", form, {
      headers: { ...form.getHeaders() },
    });
    return { success: true, data: response.data };
  } catch (err) {
    console.error("[JavaAPI] Upload failed:", err.message);
    return { success: false, error: err.message };
  }
};

// ─── Remove from Java ────────────────────────────────────────
const removeFromJava = async (documentId) => {
  try {
    const response = await javaClient.delete("/document-upload/removeDoc", {
      params: { documentId },
    });
    return { success: true, data: response.data };
  } catch (err) {
    console.error("[JavaAPI] Remove failed:", err.message);
    return { success: false, error: err.message };
  }
};

// ─── Ask — full answer collected as string ───────────────────
const askQuestion = async (question, documentIds, conversationId = null) => {
  try {
    const response = await javaClient.post(
      "/document-question-answer/ask-with-history/sync",
      { question, documentIds, conversationId },
      { headers: { "Content-Type": "application/json" }, timeout: 180000 },
    );
    return { success: true, data: response.data };
  } catch (err) {
    console.error("[JavaAPI] Ask failed:", err.message);
    return { success: false, error: err.message };
  }
};

// ─── Stream — pipes Java SSE to Node response ────────────────
const streamQuestion = async (
  question,
  documentIds,
  conversationId,
  nodeRes,
  onToken,
) => {
  try {
    const response = await javaClient.post(
      "/document-question-answer/ask-with-history/stream",
      { question, documentIds, conversationId },
      {
        headers: {
          Accept: "text/event-stream",
          "Content-Type": "application/json",
        },
        responseType: "stream",
        timeout: 180000,
      },
    );

    return new Promise((resolve, reject) => {
      response.data.on("data", (chunk) => {
        const text = chunk.toString();

        // Collect tokens via callback
        if (onToken) {
          const lines = text.split("\n");
          for (const line of lines) {
            if (line.startsWith("data:")) {
              const content = line.replace(/^data:\s*/, "").trim();
              if (
                content &&
                content !== "[DONE]" &&
                content !== "connected" &&
                !content.startsWith("Connected:")
              ) {
                onToken(content + " ");
              }
            }
          }
        }

        // Forward chunk to client
        nodeRes.write(text);
      });

      response.data.on("end", () => {
        nodeRes.end();
        resolve();
      });

      response.data.on("error", (err) => {
        console.error("[JavaAPI] Stream error:", err.message);
        nodeRes.end();
        reject(err);
      });
    });
  } catch (err) {
    console.error("[JavaAPI] Stream failed:", err.message);
    nodeRes.end();
    throw err;
  }
};
// ─── Summary document through Java ────────────────────────
const summarizeDocumentInJava = async (
  documentId,
  type = "SHORT",
  language = "GERMAN",
  storagePath,
) => {
  try {
    const response = await javaClient.get(
      `/documents/${encodeURIComponent(documentId)}/summary`,
      {
        params: { type, language, storagePath },
        timeout: 180000,
      },
    );
    return { success: true, data: response.data };
  } catch (err) {
    console.error("[JavaAPI] Summary failed:", err.message);
    return { success: false, error: err.message };
  }
};
// ─── Translate document through Java ────────────────────────
// Returns an array of { pageNo, translation } from the Java service.
// pages accepts a single page ("5"), a range ("2-3"), or is omitted
// entirely to translate the whole document, page by page.
const translateDocumentInJava = async (
  documentId,
  targetLanguage,
  storagePath,
  pages = null,
) => {
  try {
    const endpoint = `/documents/${encodeURIComponent(documentId)}/translate`;

    const params = {
      language: targetLanguage,
      storagePath,
    };

    if (pages) {
      params.pages = pages;
    }

    const response = await javaClient.get(endpoint, {
      params,
      timeout: 180000,
    });
    return { success: true, data: response.data };
  } catch (err) {
    console.error("[JavaAPI] Translate failed:", err.message);
    return { success: false, error: err.message };
  }
};
// ─── Translate document through Java — streaming ─────────────
// Same page-selection semantics as translateDocumentInJava, but pipes each
// {pageNo, translation} SSE event to nodeRes as it arrives, and calls
// onPage(pageObj) for each parsed page so the caller can accumulate them
// (e.g. for caching) without waiting for the stream to finish.
const streamTranslateDocumentInJava = async (
  documentId,
  targetLanguage,
  storagePath,
  pages,
  nodeRes,
  onPage,
) => {
  try {
    const params = {
      language: targetLanguage,
      storagePath,
    };
    if (pages) {
      params.pages = pages;
    }

    const response = await javaClient.get(
      `/documents/${encodeURIComponent(documentId)}/translate/stream`,
      {
        params,
        headers: { Accept: "text/event-stream" },
        responseType: "stream",
        timeout: 0, // translation can run long; rely on the Java side to complete/error the stream
      },
    );

    return new Promise((resolve, reject) => {
      response.data.on("data", (chunk) => {
        const text = chunk.toString();

        if (onPage) {
          const lines = text.split("\n");
          for (const line of lines) {
            if (line.startsWith("data:")) {
              const content = line.replace(/^data:\s*/, "").trim();
              if (content && content !== "[DONE]") {
                try {
                  onPage(JSON.parse(content));
                } catch (e) {
                  console.error(
                    "[JavaAPI] Failed to parse translate stream chunk:",
                    e.message,
                  );
                }
              }
            }
          }
        }

        // Forward the raw SSE chunk straight to the client
        nodeRes.write(text);
      });

      response.data.on("end", () => {
        nodeRes.end();
        resolve();
      });

      response.data.on("error", (err) => {
        console.error("[JavaAPI] Translate stream error:", err.message);
        nodeRes.end();
        reject(err);
      });
    });
  } catch (err) {
    console.error("[JavaAPI] Translate stream failed:", err.message);
    nodeRes.end();
    throw err;
  }
};
const extractDocument = async (
  filePath,
  fileName,
  documentId,
  taxonomy = [],
) => {
  try {
    const form = new FormData();
    form.append("file", fs.createReadStream(filePath), fileName);
    form.append("documentId", documentId);
    form.append("taxonomy", JSON.stringify(taxonomy));

    const response = await javaClient.post(
      "/document-extraction/extract",
      form,
      {
        headers: { ...form.getHeaders() },
      },
    );
    return { success: true, data: response.data };
  } catch (err) {
    console.error("[JavaAPI] Extraction failed:", err.message);
    return { success: false, error: err.message };
  }
};
module.exports = {
  uploadToJava,
  removeFromJava,
  askQuestion,
  streamQuestion,
  summarizeDocumentInJava,
  translateDocumentInJava,
  streamTranslateDocumentInJava,
  extractDocument,
};
