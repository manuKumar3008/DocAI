const { Document, DocumentMetadata, DocumentType } = require("../models");
const { createError } = require("../middleware/error.middleware");
const javaApiClient = require("./javaApi.client");
const storageService = require("./storage.service");
const { enrichDocumentFields } = require("../utils/enrichDocumentFields");
// ─── Get Indexer Queue ──────────────────────────────────────
const getQueue = async (filters = {}, pagination = {}) => {
  const { state, documentType } = filters;
  const { page = 1, size = 10 } = pagination;

  const query = {
    state: state ? state : { $in: ["pending_review"] },
  };

  if (documentType) query.documentType = documentType;

  const skip = (page - 1) * size;

  const [items, total] = await Promise.all([
    Document.find(query)
      .sort({ uploadedAt: -1 })
      .skip(skip)
      .limit(Number(size))
      .select(
        "documentId fileName documentType state aiConfidence missingFields uploadedAt uploaderUserId extractedFields fieldConfidence",
      ),
    Document.countDocuments(query),
  ]);

  const enrichedItems = await enrichDocumentFields(items);

  return {
    items: enrichedItems,
    total,
    page: Number(page),
    size: Number(size),
    totalPages: Math.ceil(total / size),
  };
};

// ─── Get Indexing Detail ────────────────────────────────────
const getIndexingDetail = async (documentId) => {
  const doc = await Document.findOne({
    documentId,
    state: "pending_review",
  });

  if (!doc) {
    throw createError("Document not found in indexing queue", 404, "NOT_FOUND");
  }

  const metadata = await DocumentMetadata.findOne({ documentId });

  const docType = await DocumentType.findOne({
    documentType: doc.documentType,
    active: true,
  });
  const mandatoryFields = docType?.mandatoryFields || [];
  const optionalFields = docType?.optionalFields || [];

  const extractedFields = metadata?.fields || {};

  const fieldReview = mandatoryFields.map((field) => ({
    field,
    value: extractedFields[field] || null,
    mandatory: true,
    present: !!extractedFields[field],
  }));

  optionalFields.forEach((field) => {
    fieldReview.push({
      field,
      value: extractedFields[field] || null,
      mandatory: false,
      present: !!extractedFields[field],
    });
  });

  return {
    documentId: doc.documentId,
    fileName: doc.fileName,
    documentType: doc.documentType,
    state: doc.state,
    aiConfidence: doc.aiConfidence,
    missingFields: doc.missingFields,
    mandatoryFields,
    extractedMetadata: extractedFields,
    fieldReview,
    filePreviewUrl: `/api/documents/${documentId}/file`,
    uploadedAt: doc.uploadedAt,
    uploaderUserId: doc.uploaderUserId,
  };
};

// ─── Submit Manual Indexing ─────────────────────────────────
const submitIndexing = async (
  documentId,
  { metadata, action, documentType },
  indexerUserId,
) => {
  if (!action || !["index", "reject"].includes(action)) {
    throw createError(
      'action must be "index" or "reject"',
      400,
      "VALIDATION_ERROR",
    );
  }

  const doc = await Document.findOne({
    documentId,
    state: "pending_review",
  });

  if (!doc) {
    throw createError("Document not found in indexing queue", 404, "NOT_FOUND");
  }

  // ── Reject — full cleanup everywhere ────────────────────
  if (action === "reject") {
    // Step 1 — Remove from Elasticsearch via Java
    const javaResult = await javaApiClient.removeFromJava(documentId);
    if (!javaResult.success) {
      console.warn(
        `[IndexingService] Java removal failed for ${documentId} — continuing cleanup`,
      );
    }

    // Step 2 — Delete file from disk
    await storageService.deleteFile(doc.storagePath);

    // Step 3 — Delete metadata
    await DocumentMetadata.deleteOne({ documentId });

    // Step 4 — Delete document record
    await Document.deleteOne({ documentId });

    return {
      documentId,
      state: "rejected",
      message: "Document rejected and removed from all systems",
    };
  }

  // ── Index ────────────────────────────────────────────────
  const resolvedDocumentType = documentType || doc.documentType;

  if (!resolvedDocumentType) {
    throw createError(
      "documentType is required — extraction did not detect it automatically",
      400,
      "VALIDATION_ERROR",
    );
  }

  if (!metadata || Object.keys(metadata).length === 0) {
    throw createError(
      'metadata is required when action is "index"',
      400,
      "VALIDATION_ERROR",
    );
  }

  // Validate mandatory fields
  const docType = await DocumentType.findOne({
    documentType: resolvedDocumentType,
    active: true,
  });
  const mandatoryFields = docType?.mandatoryFields || [];
  const stillMissing = mandatoryFields.filter((f) => !metadata[f]);

  if (stillMissing.length > 0) {
    throw createError(
      `Missing mandatory fields: ${stillMissing.join(", ")}`,
      400,
      "MISSING_MANDATORY_FIELDS",
    );
  }

  // Save metadata
  await DocumentMetadata.findOneAndUpdate(
    { documentId },
    { documentId, documentType: resolvedDocumentType, fields: metadata },
    { upsert: true },
  );

  // Update document to indexed
  await Document.findOneAndUpdate(
    { documentId },
    {
      state: "indexed",
      documentType: resolvedDocumentType,
      missingFields: [],
      indexedAt: new Date(),
      assignedIndexerId: indexerUserId,
      $push: {
        statusHistory: {
          from: doc.state,
          to: "indexed",
          by: indexerUserId,
          at: new Date(),
        },
      },
    },
  );

  return {
    documentId,
    state: "indexed",
    documentType: resolvedDocumentType,
    message: "Document indexed successfully",
    metadata,
  };
};

module.exports = {
  getQueue,
  getIndexingDetail,
  submitIndexing,
};
