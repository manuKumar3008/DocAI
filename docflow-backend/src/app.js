const express = require("express");
const cors = require("cors");
const morgan = require("morgan");
const path = require("path");
const { errorHandler } = require("./middleware/error.middleware");

const app = express();

app.use(cors({ origin: "*" }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(morgan("dev"));
app.use("/uploads", express.static(path.join(__dirname, "../uploads")));

// Health check
app.get("/health", (req, res) => {
  res.json({
    success: true,
    data: { status: "ok", service: "docflow-backend", timestamp: new Date() },
  });
});

// Routes
app.use("/api/auth", require("./routes/auth.routes"));
app.use("/api/documents", require("./routes/document.routes"));
app.use("/api/manual-indexing", require("./routes/indexing.routes"));
app.use("/api/conversations", require("./routes/conversation.routes"));
app.use("/api/admin", require("./routes/admin.routes"));

// 404
app.use((req, res) => {
  res.status(404).json({
    success: false,
    error: {
      message: `Route ${req.method} ${req.url} not found`,
      code: "NOT_FOUND",
    },
  });
});

// Global error handler
app.use(errorHandler);

module.exports = app;
