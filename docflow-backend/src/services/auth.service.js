const jwt = require("jsonwebtoken");
const { User, TokenBlacklist } = require("../models");
const { createError } = require("../middleware/error.middleware");

// ─── Generate Token ─────────────────────────────────────────
const generateToken = (user) => {
  return jwt.sign(
    { userId: user.userId, role: user.role },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || "8h" },
  );
};

// ─── Login ──────────────────────────────────────────────────
const login = async (email, password) => {
  const user = await User.findOne({ email: email.toLowerCase() }).select(
    "+password",
  );
  if (!user)
    throw createError("Invalid email or password", 401, "INVALID_CREDENTIALS");

  if (!user.active)
    throw createError(
      "Account has been deactivated",
      403,
      "ACCOUNT_DEACTIVATED",
    );

  const isMatch = await user.comparePassword(password);
  if (!isMatch)
    throw createError("Invalid email or password", 401, "INVALID_CREDENTIALS");

  const token = generateToken(user);

  return {
    token,
    user: {
      userId: user.userId,
      name: user.name,
      email: user.email,
      role: user.role,
    },
  };
};

// ─── Logout ─────────────────────────────────────────────────
const logout = async (token, userId) => {
  // Decode to get expiry — don't verify, just decode
  const decoded = jwt.decode(token);

  if (!decoded || !decoded.exp) {
    throw createError("Invalid token", 400, "INVALID_TOKEN");
  }

  const expiresAt = new Date(decoded.exp * 1000);

  // Check if already blacklisted
  const existing = await TokenBlacklist.findOne({ token });
  if (existing) {
    return { message: "Already logged out" };
  }

  // Add to blacklist — MongoDB TTL will auto-delete when token expires
  await TokenBlacklist.create({ token, userId, expiresAt });

  return { message: "Logged out successfully" };
};

// ─── Get Me ─────────────────────────────────────────────────
const getMe = async (userId) => {
  const user = await User.findOne({ userId });
  if (!user) throw createError("User not found", 404, "NOT_FOUND");

  return {
    userId: user.userId,
    name: user.name,
    email: user.email,
    role: user.role,
    active: user.active,
  };
};

module.exports = { login, logout, getMe };
