const { DocumentType, SystemConfig } = require("../models");
const { createError } = require("../middleware/error.middleware");

// ════════════════════════════════════════
// DOCUMENT TYPES
// ════════════════════════════════════════

// ─── List Document Types ────────────────────────────────────
const listDocumentTypes = async (filters = {}, pagination = {}) => {
  const query = {};

  // By default show only active — pass active=false to see all
  if (filters.active !== undefined) {
    query.active = filters.active === "false" ? false : true;
  }

  const page = Math.max(1, parseInt(pagination.page) || 1);
  const limit = Math.max(1, parseInt(pagination.limit) || 10);
  const skip = (page - 1) * limit;

  const [types, total] = await Promise.all([
    DocumentType.find(query).sort({ documentType: 1 }).skip(skip).limit(limit),
    DocumentType.countDocuments(query),
  ]);

  return {
    items: types,
    total,
    page,
    limit,
    totalPages: Math.ceil(total / limit),
  };
};

// ─── Get Single Document Type ───────────────────────────────
const getDocumentType = async (documentType) => {
  const type = await DocumentType.findOne({ documentType });
  if (!type) throw createError("Document type not found", 404, "NOT_FOUND");
  return type;
};

// ─── Create Document Type ───────────────────────────────────
const createDocumentType = async ({
  documentType,
  displayName,
  description,
  mandatoryFields,
  optionalFields,
}) => {
  // Validate required fields
  if (!documentType || !displayName) {
    throw createError(
      "documentType and displayName are required",
      400,
      "VALIDATION_ERROR",
    );
  }

  // Check duplicate
  const existing = await DocumentType.findOne({ documentType });
  if (existing) {
    throw createError(
      `Document type '${documentType}' already exists`,
      409,
      "DUPLICATE",
    );
  }

  // Validate no overlap between mandatory and optional
  const mandatory = mandatoryFields || [];
  const optional = optionalFields || [];
  const overlap = mandatory.filter((f) => optional.includes(f));
  if (overlap.length > 0) {
    throw createError(
      `Fields cannot be both mandatory and optional: ${overlap.join(", ")}`,
      400,
      "VALIDATION_ERROR",
    );
  }

  const type = new DocumentType({
    documentType: documentType.toUpperCase(),
    displayName,
    description: description || "",
    active: true,
    mandatoryFields: mandatory,
    optionalFields: optional,
  });

  await type.save();
  return type;
};

// ─── Update Document Type ───────────────────────────────────
const updateDocumentType = async (documentType, updates) => {
  const type = await DocumentType.findOne({ documentType });
  if (!type) throw createError("Document type not found", 404, "NOT_FOUND");

  const allowed = [
    "displayName",
    "mandatoryFields",
    "description",
    "optionalFields",
    "active",
  ];
  allowed.forEach((field) => {
    if (updates[field] !== undefined) {
      type[field] = updates[field];
    }
  });

  // Validate no overlap after update
  const overlap = type.mandatoryFields.filter((f) =>
    type.optionalFields.includes(f),
  );
  if (overlap.length > 0) {
    throw createError(
      `Fields cannot be both mandatory and optional: ${overlap.join(", ")}`,
      400,
      "VALIDATION_ERROR",
    );
  }

  await type.save();
  return type;
};

// ─── Delete (deactivate) Document Type ─────────────────────
const deleteDocumentType = async (documentType) => {
  const type = await DocumentType.findOne({ documentType });
  if (!type) throw createError("Document type not found", 404, "NOT_FOUND");

  // Soft delete — deactivate instead of removing
  // Hard delete would break existing documents referencing this type
  type.active = false;
  await type.save();

  return { message: `Document type '${documentType}' deactivated` };
};

// ════════════════════════════════════════
// CONFIDENCE THRESHOLD
// ════════════════════════════════════════

// ─── Get Confidence Threshold ───────────────────────────────
const getConfidenceThreshold = async () => {
  const config = await SystemConfig.findOne({
    configKey: "confidenceThreshold",
  });

  // Return env value or DB value or default
  const envValue = parseFloat(process.env.CONFIDENCE_THRESHOLD);
  const dbValue = config?.value?.threshold;
  const current = dbValue || envValue || 0.8;

  return {
    threshold: current,
    source: dbValue ? "database" : envValue ? "environment" : "default",
    note: "Documents with AI confidence below this value go to pending_review with missing fields flagged",
  };
};

// ─── Set Confidence Threshold ───────────────────────────────
const setConfidenceThreshold = async (threshold, updatedBy) => {
  if (threshold === undefined || threshold === null) {
    throw createError("threshold is required", 400, "VALIDATION_ERROR");
  }

  const value = parseFloat(threshold);

  if (isNaN(value) || value < 0 || value > 1) {
    throw createError(
      "threshold must be a number between 0 and 1",
      400,
      "VALIDATION_ERROR",
    );
  }

  const config = await SystemConfig.findOneAndUpdate(
    { configKey: "confidenceThreshold" },
    {
      configKey: "confidenceThreshold",
      value: { threshold: value },
      updatedBy,
    },
    { upsert: true, new: true },
  );

  return {
    threshold: config.value.threshold,
    updatedBy,
    updatedAt: config.updatedAt,
  };
};

module.exports = {
  listDocumentTypes,
  getDocumentType,
  createDocumentType,
  updateDocumentType,
  deleteDocumentType,
  getConfidenceThreshold,
  setConfidenceThreshold,
};
