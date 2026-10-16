const asyncHandler = require("../utils/asyncHandler");
const { sendSuccess, sendError } = require("../utils/apiResponse");
const userService = require("../services/user.service");
const adminService = require("../services/admin.service");

// ════════════════════════════════════════
// USER MANAGEMENT
// ════════════════════════════════════════

exports.listUsers = asyncHandler(async (req, res) => {
  const { role, active, page, limit } = req.query;
  const users = await userService.listUsers({ role, active }, { page, limit });
  sendSuccess(res, { items: users, total: users.length });
});

exports.createUser = asyncHandler(async (req, res) => {
  const { name, email, password, role } = req.body;
  const user = await userService.createUser({ name, email, password, role });
  sendSuccess(res, user, 201);
});

exports.getUser = asyncHandler(async (req, res) => {
  const user = await userService.getUserById(req.params.userId);
  sendSuccess(res, user);
});

exports.updateUser = asyncHandler(async (req, res) => {
  const user = await userService.updateUser(req.params.userId, req.body);
  sendSuccess(res, user);
});

exports.deleteUser = asyncHandler(async (req, res) => {
  const result = await userService.deleteUser(
    req.params.userId,
    req.user.userId,
  );
  sendSuccess(res, result);
});

exports.resetPassword = asyncHandler(async (req, res) => {
  const { newPassword } = req.body;
  const result = await userService.resetPassword(
    req.params.userId,
    newPassword,
  );
  sendSuccess(res, result);
});

// ════════════════════════════════════════
// DOCUMENT TYPES
// ════════════════════════════════════════

exports.listDocumentTypes = asyncHandler(async (req, res) => {
  const { active, page, limit } = req.query;
  const result = await adminService.listDocumentTypes({ active }, { page, limit });
  sendSuccess(res, result);
});

exports.getDocumentType = asyncHandler(async (req, res) => {
  const result = await adminService.getDocumentType(req.params.type);
  sendSuccess(res, result);
});

exports.createDocumentType = asyncHandler(async (req, res) => {
  const {
    documentType,
    displayName,
    description,
    mandatoryFields,
    optionalFields,
  } = req.body;
  const result = await adminService.createDocumentType({
    documentType,
    displayName,
    description,
    mandatoryFields,
    optionalFields,
  });
  sendSuccess(res, result, 201);
});

exports.updateDocumentType = asyncHandler(async (req, res) => {
  const result = await adminService.updateDocumentType(
    req.params.type,
    req.body,
  );
  sendSuccess(res, result);
});

exports.deleteDocumentType = asyncHandler(async (req, res) => {
  const result = await adminService.deleteDocumentType(req.params.type);
  sendSuccess(res, result);
});

// ════════════════════════════════════════
// CONFIDENCE THRESHOLD
// ════════════════════════════════════════

exports.getThreshold = asyncHandler(async (req, res) => {
  const result = await adminService.getConfidenceThreshold();
  sendSuccess(res, result);
});

exports.setThreshold = asyncHandler(async (req, res) => {
  const { threshold } = req.body;
  const result = await adminService.setConfidenceThreshold(
    threshold,
    req.user.userId,
  );
  sendSuccess(res, result);
});
