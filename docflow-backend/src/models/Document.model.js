const mongoose = require("mongoose");

const statusHistorySchema = new mongoose.Schema(
  {
    from: { type: String, default: null },
    to: { type: String, required: true },
    by: { type: String, required: true },
    at: { type: Date, default: Date.now },
  },
  { _id: false },
);

const documentSchema = new mongoose.Schema(
  {
    documentId: { type: String, required: true, unique: true }, // unique: true = index
    fileName: { type: String, required: true },
    fileHash: { type: String, default: null, index: true },
    documentType: { type: String, default: null, index: true }, // add index here
    source: {
      type: String,
      enum: ["MANUAL_UPLOAD", "SHARED_DRIVE"],
      required: true,
    },
    state: {
      type: String,
      enum: [
        "uploaded",
        "extracting",
        "pending_review",
        "indexed",
        "rejected",
        "failed",
      ],
      default: "uploaded",
      index: true, // add index here
    },
    storagePath: { type: String, required: true },
    pageCount: {
      type: Number,
      default: 0,
    },
    uploaderUserId: { type: String, required: true, index: true },
    assignedIndexerId: { type: String, default: null },
    aiConfidence: { type: Number, default: null },
    extractedFields: { type: mongoose.Schema.Types.Mixed, default: {} },
    fieldConfidence: { type: mongoose.Schema.Types.Mixed, default: {} },
    missingFields: [{ type: String }],
    manualIndexing: {
      queueStatus: {
        type: String,
        enum: ["pending", "in_progress", "completed", null],
        default: null,
      },
      assignedIndexerId: { type: String, default: null },
      createdAt: { type: Date, default: null },
      extractedSnapshot: { type: mongoose.Schema.Types.Mixed, default: null },
    },
    summary: { type: String, default: null },
    translation: {
      targetLanguage: { type: String, default: null },
      content: { type: String, default: null },
    },
    uploadedAt: { type: Date, default: Date.now },
    extractedAt: { type: Date, default: null },
    indexedAt: { type: Date, default: null },
    statusHistory: [statusHistorySchema],
  },
  { timestamps: true },
);

module.exports = mongoose.model("Document", documentSchema);
