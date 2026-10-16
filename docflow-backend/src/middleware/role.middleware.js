const { createError } = require("../middleware/error.middleware");

const authorize =
  (...roles) =>
  (req, res, next) => {
    if (!req.user) {
      throw createError("Not authenticated", 401, "UNAUTHORIZED");
    }
    if (!roles.includes(req.user.role)) {
      throw createError(
        `Role ${req.user.role} is not allowed to access this route`,
        403,
        "FORBIDDEN",
      );
    }
    next();
  };

module.exports = { authorize };
