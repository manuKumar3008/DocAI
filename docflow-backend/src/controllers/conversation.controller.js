const asyncHandler = require("../utils/asyncHandler");
const { sendSuccess, sendError } = require("../utils/apiResponse");
const { protect } = require("../middleware/auth.middleware");
const { authorize } = require("../middleware/role.middleware");
const conversationService = require("../services/conversation.service");

// ─── POST /api/conversations ────────────────────────────────
// Roles: PROCESSOR, ADMIN
exports.createConversation = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { documentIds, title } = req.body;

    const result = await conversationService.createConversation({
      documentIds,
      title,
      createdBy: req.user.userId,
    });

    sendSuccess(res, result, 201);
  }),
];

// ─── GET /api/conversations ─────────────────────────────────
// Roles: PROCESSOR sees own, ADMIN sees all
exports.listConversations = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { page, size } = req.query;

    // PROCESSOR sees only their own conversations
    // ADMIN sees all
    const filters = {};
    if (req.user.role === "PROCESSOR") {
      filters.createdBy = req.user.userId;
    }

    const result = await conversationService.listConversations(filters, {
      page,
      size,
    });

    sendSuccess(res, result);
  }),
];

// ─── GET /api/conversations/:id ─────────────────────────────
exports.getConversation = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const result = await conversationService.getConversation(req.params.id);
    sendSuccess(res, result);
  }),
];

// ─── POST /api/conversations/:id/ask ───────────────────────
// Supports both SSE and sync based on Accept header
exports.askQuestion = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { question } = req.body;
    const { id: conversationId } = req.params;
    const acceptHeader = req.headers["accept"] || "";

    // Check if client wants SSE stream
    const wantsStream = acceptHeader.includes("text/event-stream");

    if (wantsStream) {
      // SSE streaming — pipe Java stream to client
      await conversationService.askStream(
        conversationId,
        question,
        req.user.userId,
        res,
      );
    } else {
      // Sync JSON — wait for full answer
      const result = await conversationService.askSync(
        conversationId,
        question,
        req.user.userId,
      );
      sendSuccess(res, result);
    }
  }),
];

// ─── GET /api/conversations/:id/messages ───────────────────
exports.getMessages = [
  protect,
  authorize("PROCESSOR", "ADMIN"),
  asyncHandler(async (req, res) => {
    const { page, size } = req.query;
    const result = await conversationService.getMessages(req.params.id, {
      page,
      size,
    });
    sendSuccess(res, result);
  }),
];
