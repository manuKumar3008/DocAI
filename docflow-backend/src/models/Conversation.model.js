const mongoose = require("mongoose");

const conversationSchema = new mongoose.Schema(
  {
    conversationId: { type: String, required: true, unique: true }, // unique: true = index
    title: { type: String, default: "New Conversation" },
    documentIds: [{ type: String, required: true }],
    createdBy: { type: String, required: true, index: true },
    lastActivityAt: { type: Date, default: Date.now },
  },
  { timestamps: true },
);

module.exports = mongoose.model("Conversation", conversationSchema);
