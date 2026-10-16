const asyncHandler = require("../utils/asyncHandler");
const { sendSuccess, sendError } = require("../utils/apiResponse");
const { protect } = require("../middleware/auth.middleware");
const { authorize } = require("../middleware/role.middleware");
const indexingService = require("../services/indexing.service");

// ─── GET /api/manual-indexing/queue ────────────────────────
// Roles: ADMIN, INDEXER
exports.getQueue = [
  protect,
  authorize("ADMIN", "INDEXER"),
  asyncHandler(async (req, res) => {
    const { state, documentType, page, size } = req.query;

    const result = await indexingService.getQueue(
      { state, documentType },
      { page, size },
    );

    sendSuccess(res, result);
  }),
];

// ─── GET /api/manual-indexing/:documentId ──────────────────
// Roles: ADMIN, INDEXER
exports.getIndexingDetail = [
  protect,
  authorize("ADMIN", "INDEXER"),
  asyncHandler(async (req, res) => {
    const result = await indexingService.getIndexingDetail(
      req.params.documentId,
    );
    sendSuccess(res, result);
  }),
];

// ─── POST /api/manual-indexing/:documentId/submit ──────────
// Roles: INDEXER only (Admin shouldn't bypass review)
exports.submitIndexing = [
  protect,
  authorize("ADMIN", "INDEXER"),
  asyncHandler(async (req, res) => {
    const { metadata, action, documentType } = req.body; // ← ADD documentType

    const result = await indexingService.submitIndexing(
      req.params.documentId,
      { metadata, action, documentType }, // ← PASS documentType
      req.user.userId,
    );

    sendSuccess(res, result);
  }),
];
