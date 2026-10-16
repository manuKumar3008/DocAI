const asyncHandler = require("../utils/asyncHandler");
const { sendSuccess, sendError } = require("../utils/apiResponse");
const authService = require("../services/auth.service");
const userService = require("../services/user.service");
const { protect } = require("../middleware/auth.middleware");

// ─── POST /api/auth/login ───────────────────────────────────
exports.login = asyncHandler(async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return sendError(
      res,
      "Email and password are required",
      400,
      "VALIDATION_ERROR",
    );
  }
  const result = await authService.login(email, password);
  sendSuccess(res, result);
});

// ─── GET /api/auth/me ───────────────────────────────────────
exports.me = [
  protect,
  asyncHandler(async (req, res) => {
    const result = await authService.getMe(req.user.userId);
    sendSuccess(res, result);
  }),
];

// ─── POST /api/auth/logout ──────────────────────────────────
exports.logout = [
  protect,
  asyncHandler(async (req, res) => {
    const result = await authService.logout(req.token, req.user.userId);
    sendSuccess(res, result);
  }),
];

// ─── PUT /api/auth/change-password ─────────────────────────
exports.changePassword = [
  protect,
  asyncHandler(async (req, res) => {
    const { currentPassword, newPassword } = req.body;
    const result = await userService.changePassword(
      req.user.userId,
      currentPassword,
      newPassword,
    );
    sendSuccess(res, result);
  }),
];
