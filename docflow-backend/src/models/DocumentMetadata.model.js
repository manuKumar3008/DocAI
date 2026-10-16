const mongoose = require("mongoose");

const documentMetadataSchema = new mongoose.Schema(
  {
    documentId: { type: String, required: true, unique: true }, // unique: true = index
    documentType: { type: String, required: true, index: true }, // index here
    fields: { type: mongoose.Schema.Types.Mixed, default: {} },
  },
  { timestamps: true },
);

module.exports = mongoose.model("DocumentMetadata", documentMetadataSchema);
