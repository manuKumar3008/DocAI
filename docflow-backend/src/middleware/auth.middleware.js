const jwt = require("jsonwebtoken");
const { User, TokenBlacklist } = require("../models");
const asyncHandler = require("../utils/asyncHandler");
const { createError } = require("../middleware/error.middleware");

const protect = asyncHandler(async (req, res, next) => {
  // ── Step 1: Get token from header ───────────────────────
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw createError("No token provided", 401, "UNAUTHORIZED");
  }

  const token = authHeader.split(" ")[1];

  // ── Step 2: Check blacklist ─────────────────────────────
  const blacklisted = await TokenBlacklist.findOne({ token });
  if (blacklisted) {
    throw createError(
      "Token has been invalidated. Please login again.",
      401,
      "TOKEN_BLACKLISTED",
    );
  }

  // ── Step 3: Verify token signature + expiry ─────────────
  let decoded;
  try {
    decoded = jwt.verify(token, process.env.JWT_SECRET);
  } catch (err) {
    if (err.name === "TokenExpiredError") {
      throw createError(
        "Session expired. Please login again.",
        401,
        "TOKEN_EXPIRED",
      );
    }
    throw createError(
      "Invalid token. Please login again.",
      401,
      "UNAUTHORIZED",
    );
  }

  // ── Step 4: Find user ────────────────────────────────────
  const user = await User.findOne({ userId: decoded.userId, active: true });
  if (!user) {
    throw createError("User not found or deactivated", 401, "UNAUTHORIZED");
  }

  // ── Attach user to request ───────────────────────────────
  req.user = user;
  req.token = token; // ← attach token so logout can access it
  next();
});

module.exports = { protect };
