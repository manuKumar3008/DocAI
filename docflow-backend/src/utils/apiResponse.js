const sendSuccess = (res, data, statusCode = 200) => {
  res.status(statusCode).json({
    success: true,
    data,
  });
};

const sendError = (res, message, statusCode = 500, code = "INTERNAL_ERROR") => {
  res.status(statusCode).json({
    success: false,
    error: { message, code },
  });
};

module.exports = { sendSuccess, sendError };
