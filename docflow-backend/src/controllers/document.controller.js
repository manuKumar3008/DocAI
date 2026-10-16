const path = require("path");
const asyncHandler = require("../utils/asyncHandler");
const { sendSuccess, sendError } = require("../utils/apiResponse");
const { protect } = require("../middleware/auth.middleware");
const { authorize } = require("../middleware/role.middleware");
const documentService = require("../services/document.service");
const aiActionService = require("../services/aiAction.service");
const documentTypeModel = require("../models/DocumentType.model");
const { enrichDocumentFields } = require("../utils/enrichDocumentFields");

// ─── POST /api/documents ────────────────────────────────────
// Roles: ADMIN, INDEXER
exports.uploadDocument = [
  protect,
  authorize("ADMIN", "INDEXER", "PROCESSOR"),
  asyncHandler(async (req, res) => {
    const { forceReindex } = req.body;

    const result = await documentService.uploadDocument({
      file: req.file,

      forceReindex: forceReindex === "true",
      uploaderUserId: req.user.userId,
    });

    sendSuccess(res, result, 201);
  }),
];

// ─── GET /api/documents ─────────────────────────────────────
exports.listDocuments = [
  protect,
  asyncHandler(async (req, res) => {
    const { state, documentType, page, size } = req.query;
    const filters = { state, documentType };

    if (req.user.role === "PROCESSOR") {
      filters.state = filters.state || {
        $in: ["indexed", "in_progress", "completed"],
      };
    }

    const result = await documentService.listDocuments(filters, { page, size });
    const enrichedItems = await enrichDocumentFields(result.items);

    sendSuccess(res, { ...result, items: enrichedItems });
  }),
];

// ─── GET /api/documents/:documentId ────────────────────────
// Roles: all
exports.getDocument = [
  protect,
  asyncHandler(async (req, res) => {
    const doc = await documentService.getDocument(req.params.documentId);
    sendSuccess(res, doc);
  }),
];

// ─── GET /api/documents/:documentId/file ───────────────────
// Roles: all
exports.getDocumentFile = [
  protect,
  asyncHandler(async (req, res) => {
    const { filePath, fileName } = await documentService.getDocumentFile(
      req.params.documentId,
    );

    // Set headers and stream file to client
    res.setHeader("Content-Disposition", `inline; filename="${fileName}"`);
    res.sendFile(filePath);
  }),
];

// ─── DELETE /api/documents/:documentId ─────────────────────
// Roles: ADMIN only
exports.deleteDocument = [
  protect,
  authorize("ADMIN"),
  asyncHandler(async (req, res) => {
    const result = await documentService.deleteDocument(req.params.documentId);
    sendSuccess(res, result);
  }),
];

// ─── POST /api/documents/:documentId/summarize ─────────────
exports.summarizeDocument = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { length, language, forceRefresh } = req.body;

    const result = await aiActionService.summarizeDocument(
      req.params.documentId,
      {
        length,
        language,
        forceRefresh: forceRefresh === true,
      },
    );

    sendSuccess(res, result);
  }),
];
// ─── POST /api/documents/:documentId/translate ─────────────
// pages accepts a single page ("5"), a range ("2-3"), or is omitted
// to translate the whole document, page by page (0-based).
exports.translateDocument = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { targetLanguage, forceRefresh, pages } = req.body;

    const result = await aiActionService.translateDocument(
      req.params.documentId,
      {
        targetLanguage,
        forceRefresh: forceRefresh === true,
        pages: pages ?? null,
      },
    );

    sendSuccess(res, result);
  }),
];

// ─── POST /api/documents/:documentId/translate/stream ──────
// Streams pages as they're translated, instead of waiting for the whole
// document. pages accepts a single page ("5"), a range ("2-3"), or is
// omitted for the whole document. POST (not a native EventSource) so the
// existing `protect` auth middleware — which reads the Authorization
// header — keeps working the same way it does for every other route.
exports.translateDocumentStream = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { targetLanguage, forceRefresh, pages } = req.body;

    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("Connection", "keep-alive");
    res.flushHeaders();

    await aiActionService.streamTranslateDocument(
      req.params.documentId,
      {
        targetLanguage,
        forceRefresh: forceRefresh === true,
        pages: pages ?? null,
      },
      res,
    );
  }),
];
