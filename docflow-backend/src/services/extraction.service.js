// ─────────────────────────────────────────────────────────────
// Extraction Service
// Owns ALL communication with Java for a document:
//   1. Upload (indexing + embedding) — implemented
//   2. Metadata extraction           — implemented (taxonomy-driven)
// ─────────────────────────────────────────────────────────────

const {
  Document,
  DocumentMetadata,
  DocumentType,
  SystemConfig,
} = require("../models");
const javaApiClient = require("./javaApi.client");

// ─── Step 1: Upload to Java for indexing ───────────────────
// Sends file to Java for chunking + embedding into Elasticsearch
const uploadForIndexing = async (
  filePath,
  fileName,
  documentId,
  forceReindex = false,
) => {
  const result = await javaApiClient.uploadToJava(
    filePath,
    fileName,
    documentId,
    forceReindex,
  );
  return result; // { success, data, error }
};

// ─── Step 2: Extract metadata via Java ─────────────────────
// Passes the Admin-configured document type taxonomy so Java classifies
// only against types you control, instead of the external masterdata taxonomy.
const extractMetadata = async (documentId, filePath, fileName) => {
  const documentTypes = await DocumentType.find({ active: true })
    .select("_id documentType description mandatoryFields")
    .lean();

  const taxonomy = documentTypes.map((dt) => ({
    id: dt._id.toString(),
    name: dt.documentType,
    description: dt.description || "",
    mandatoryFields: dt.mandatoryFields || [],
  }));

  const response = await javaApiClient.extractDocument(
    filePath,
    fileName,
    documentId,
    taxonomy,
  );
  if (!response.success) return null;
  return response.data; // { documentType: { id, name, confidence }, fields, confidenceScore, fieldConfidence }
};

// ─── Step 3: Route document after extraction ────────────────
// Decides state based on extraction result + confidence threshold
const routeAfterExtraction = async (documentId, extractionResult) => {
  // No extraction result — always pending_review for now
  if (!extractionResult) {
    await Document.findOneAndUpdate(
      { documentId },
      {
        state: "pending_review",
        extractedAt: new Date(),
        $push: {
          statusHistory: {
            from: "extracting",
            to: "pending_review",
            by: "system",
            at: new Date(),
          },
        },
      },
    );
    return "pending_review";
  }

  const { documentType, fields, confidenceScore, fieldConfidence } =
    extractionResult;
  // documentType is now { id, name, confidence } — id is null/undefined when Java returned UNKNOWN

  const threshold =
    parseFloat(process.env.CONFIDENCE_THRESHOLD) ||
    (await getThresholdFromDB()) ||
    0.8;

  const isUnknown = !documentType?.id;
  const docType = isUnknown
    ? null
    : await DocumentType.findById(documentType.id);

  const documentTypeName = documentType?.name || "UNKNOWN";
  const mandatoryFields = docType?.mandatoryFields || [];
  const missingFields = mandatoryFields.filter((f) => !fields?.[f]);

  const confidenceOk = confidenceScore >= threshold;
  const allFieldsPresent = missingFields.length === 0;

  if (!isUnknown && confidenceOk && allFieldsPresent) {
    // Perfect extraction → directly indexed, no Indexer needed
    await DocumentMetadata.findOneAndUpdate(
      { documentId },
      { documentId, documentType: documentTypeName, fields },
      { upsert: true },
    );

    await Document.findOneAndUpdate(
      { documentId },
      {
        state: "indexed",
        documentType: documentTypeName,
        aiConfidence: confidenceScore,
        missingFields: [],
        extractedFields: fields || {},
        fieldConfidence: fieldConfidence || {},
        extractedAt: new Date(),
        indexedAt: new Date(),
        $push: {
          statusHistory: {
            from: "extracting",
            to: "indexed",
            by: "system",
            at: new Date(),
          },
        },
      },
    );
    return "indexed";
  } else {
    // Low confidence, missing fields, or unknown type → pending_review for Indexer
    if (fields && Object.keys(fields).length > 0) {
      await DocumentMetadata.findOneAndUpdate(
        { documentId },
        { documentId, documentType: documentTypeName, fields },
        { upsert: true },
      );
    }

    await Document.findOneAndUpdate(
      { documentId },
      {
        state: "pending_review",
        documentType: documentTypeName,
        aiConfidence: confidenceScore,
        missingFields,
        extractedFields: fields || {},
        fieldConfidence: fieldConfidence || {},
        extractedAt: new Date(),
        $push: {
          statusHistory: {
            from: "extracting",
            to: "pending_review",
            by: "system",
            at: new Date(),
          },
        },
      },
    );
    return "pending_review";
  }
};

// ─── Main entry point: process document after upload ───────
// Called by document.service.js after file is saved to disk
const processDocument = async ({
  documentId,
  filePath,
  fileName,
  forceReindex,
}) => {
  // Step 1 — Upload to Java (indexing + embedding)
  const uploadResult = await uploadForIndexing(
    filePath,
    fileName,
    documentId,
    forceReindex,
  );

  if (!uploadResult.success) {
    return { success: false, error: uploadResult.error };
  }

  // Step 2 — Extract metadata (taxonomy-driven classification)
  const extractionResult = await extractMetadata(
    documentId,
    filePath,
    fileName,
  );

  // Step 3 — Route document based on extraction result
  const finalState = await routeAfterExtraction(documentId, extractionResult);

  return { success: true, state: finalState };
};

// ─── Helper: get threshold from MongoDB config ──────────────
const getThresholdFromDB = async () => {
  const config = await SystemConfig.findOne({
    configKey: "confidenceThreshold",
  });
  return config?.value?.threshold || null;
};

module.exports = {
  processDocument,
  routeAfterExtraction,
  extractMetadata,
  uploadForIndexing,
};
