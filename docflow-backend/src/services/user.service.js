const { User } = require("../models");
const { generateUserId } = require("../utils/generateId");
const { createError } = require("../middleware/error.middleware");

// ─── List all users ────────────────────────────────────────
const listUsers = async (filters = {}, pagination = {}) => {
  const query = {};

  if (filters.role) query.role = filters.role;
  if (filters.active !== undefined) query.active = filters.active === "true";

  const page = parseInt(pagination.page) || 1;
  const limit = parseInt(pagination.limit) || 10;
  const skip = (page - 1) * limit;

  const [users, total] = await Promise.all([
    User.find(query).sort({ createdAt: -1 }).skip(skip).limit(limit),
    User.countDocuments(query),
  ]);

  return {
    items: users,
    total,
    page,
    limit,
    totalPages: Math.ceil(total / limit),
  };
};

// ─── Get single user ───────────────────────────────────────
const getUserById = async (userId) => {
  const user = await User.findOne({ userId });
  if (!user) throw createError("User not found", 404, "NOT_FOUND");
  return user;
};

// ─── Create user ───────────────────────────────────────────
const createUser = async ({ name, email, password, role }) => {
  // check required fields
  if (!name || !email || !password || !role) {
    throw createError(
      "name, email, password and role are required",
      400,
      "VALIDATION_ERROR",
    );
  }

  // check valid role
  const validRoles = ["ADMIN", "INDEXER", "PROCESSOR"];
  if (!validRoles.includes(role)) {
    throw createError(
      `role must be one of: ${validRoles.join(", ")}`,
      400,
      "VALIDATION_ERROR",
    );
  }

  // check duplicate email
  const existing = await User.findOne({ email: email.toLowerCase() });
  if (existing) {
    throw createError(
      "A user with this email already exists",
      409,
      "DUPLICATE_EMAIL",
    );
  }

  const user = new User({
    userId: generateUserId(),
    name,
    email,
    password,
    role,
    active: true,
  });

  await user.save();
  return user;
};

// ─── Update user ───────────────────────────────────────────
const updateUser = async (userId, updates) => {
  const user = await User.findOne({ userId });
  if (!user) throw createError("User not found", 404, "NOT_FOUND");

  // only these fields can be updated
  const allowed = ["name", "role", "active"];
  allowed.forEach((field) => {
    if (updates[field] !== undefined) {
      user[field] = updates[field];
    }
  });

  // validate role if being changed
  if (updates.role) {
    const validRoles = ["ADMIN", "INDEXER", "PROCESSOR"];
    if (!validRoles.includes(updates.role)) {
      throw createError(
        `role must be one of: ${validRoles.join(", ")}`,
        400,
        "VALIDATION_ERROR",
      );
    }
  }

  await user.save();
  return user;
};

// ─── Delete user ───────────────────────────────────────────
const deleteUser = async (userId, requestingUserId) => {
  // prevent self-deletion
  if (userId === requestingUserId) {
    throw createError("You cannot delete your own account", 400, "SELF_DELETE");
  }

  const user = await User.findOne({ userId });
  if (!user) throw createError("User not found", 404, "NOT_FOUND");

  await User.deleteOne({ userId });
  return { message: "User deleted successfully" };
};

// ─── Change password (by the user themselves) ──────────────
const changePassword = async (userId, currentPassword, newPassword) => {
  if (!currentPassword || !newPassword) {
    throw createError(
      "currentPassword and newPassword are required",
      400,
      "VALIDATION_ERROR",
    );
  }

  if (newPassword.length < 6) {
    throw createError(
      "newPassword must be at least 6 characters",
      400,
      "VALIDATION_ERROR",
    );
  }

  // fetch with password (normally excluded)
  const user = await User.findOne({ userId }).select("+password");
  if (!user) throw createError("User not found", 404, "NOT_FOUND");

  const isMatch = await user.comparePassword(currentPassword);
  if (!isMatch) {
    throw createError("Current password is incorrect", 401, "INVALID_PASSWORD");
  }

  user.password = newPassword;
  await user.save(); // pre-save hook hashes it automatically
  return { message: "Password changed successfully" };
};

// ─── Admin reset password ──────────────────────────────────
const resetPassword = async (userId, newPassword) => {
  if (!newPassword || newPassword.length < 6) {
    throw createError(
      "newPassword must be at least 6 characters",
      400,
      "VALIDATION_ERROR",
    );
  }

  const user = await User.findOne({ userId });
  if (!user) throw createError("User not found", 404, "NOT_FOUND");

  user.password = newPassword;
  await user.save();
  return { message: "Password reset successfully" };
};

module.exports = {
  listUsers,
  getUserById,
  createUser,
  updateUser,
  deleteUser,
  changePassword,
  resetPassword,
};
