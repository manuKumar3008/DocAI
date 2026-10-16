const mongoose = require("mongoose");

const documentTypeSchema = new mongoose.Schema(
  {
    documentType: { type: String, required: true, unique: true }, // unique: true = index
    displayName: { type: String, required: true },
    active: { type: Boolean, default: true },
    mandatoryFields: [{ type: String }],
    description: { type: String, default: "" },
    optionalFields: [{ type: String }],
  },
  { timestamps: true },
);

module.exports = mongoose.model("DocumentType", documentTypeSchema);
