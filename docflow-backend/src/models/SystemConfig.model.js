const mongoose = require("mongoose");

const systemConfigSchema = new mongoose.Schema(
  {
    configKey: { type: String, required: true, unique: true }, // unique: true = index
    value: { type: mongoose.Schema.Types.Mixed, required: true },
    updatedBy: { type: String, default: "system" },
  },
  { timestamps: true },
);

module.exports = mongoose.model("SystemConfig", systemConfigSchema);
