const errorHandler = (err, req, res, next) => {
  console.error(`[ERROR] ${err.message}`);

  const statusCode = err.statusCode || 500;
  const message = err.message || 'Internal server error';
  const code = err.code || 'INTERNAL_ERROR';

  res.status(statusCode).json({
    success: false,
    error: { message, code },
  });
};

const createError = (message, statusCode = 500, code = 'INTERNAL_ERROR') => {
  const err = new Error(message);
  err.statusCode = statusCode;
  err.code = code;
  return err;
};

module.exports = { errorHandler, createError };