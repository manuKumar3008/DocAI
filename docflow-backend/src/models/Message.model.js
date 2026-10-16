const mongoose = require("mongoose");

const ReferenceSchema = new mongoose.Schema(
  {
    page: { type: Number, default: null },
    pageLabel: { type: String, default: null },
    section: { type: String, default: null },
    chunkId: { type: String, default: null },
    snippet: { type: String, default: null },
    score: { type: Number, default: null },
  },
  { _id: false },
);

const MessageSchema = new mongoose.Schema(
  {
    conversationId: { type: String, required: true, index: true },
    sequence: { type: Number, required: true },
    role: {
      type: String,
      enum: ["user", "assistant", "system"],
      required: true,
    },
    content: { type: String, required: true },
    references: {
      type: [ReferenceSchema],
      default: [],
    },
    suggestedFollowUps: {
      type: [String],
      default: [],
    },
    createdAt: { type: Date, default: Date.now },
  },
  { timestamps: false },
);

module.exports = mongoose.model("Message", MessageSchema);
