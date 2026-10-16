const crypto = require("crypto");
const fs = require("fs");
const fsPromises = require("fs/promises");
const { PDFDocument } = require("pdf-lib");

const { Document, DocumentMetadata } = require("../models");
const { generateDocumentId } = require("../utils/generateId");
const { createError } = require("../middleware/error.middleware");
const storageService = require("./storage.service");
const extractionService = require("./extraction.service");

// ─── Generate file hash for duplicate detection ─────────────
const generateFileHash = (filePath) => {
  const fileBuffer = fs.readFileSync(filePath);
  return crypto.createHash("md5").update(fileBuffer).digest("hex");
};

// ─── Get PDF page count ─────────────────────────────────────
const getPdfPageCount = async (filePath) => {
  const pdfBytes = await fsPromises.readFile(filePath);
  const pdfDoc = await PDFDocument.load(pdfBytes);
  return pdfDoc.getPageCount();
};

// ─── Upload Document ────────────────────────────────────────
const uploadDocument = async ({ file, forceReindex, uploaderUserId }) => {
  if (!file) throw createError("File is required", 400, "VALIDATION_ERROR");

  const fileHash = generateFileHash(file.path);
  const duplicate = await Document.findOne({
    fileHash,
    state: { $nin: ["rejected", "failed"] },
  });

  if (duplicate) {
    await storageService.deleteFile(file.path);
    throw createError(
      `Document already uploaded. Existing ID: ${duplicate.documentId}`,
      409,
      "DUPLICATE_DOCUMENT",
    );
  }

  const documentId = generateDocumentId();

  let pageCount = 0;
  try {
    pageCount = await getPdfPageCount(file.path);
  } catch (err) {
    console.warn(
      `[DocumentService] Failed to read page count for ${file.originalname}: ${err.message}`,
    );
  }

  const doc = new Document({
    documentId,
    fileName: file.originalname,
    documentType: null,
    source: "MANUAL_UPLOAD",
    state: "uploaded",
    storagePath: file.path,
    fileHash,
    pageCount,
    uploaderUserId,
    uploadedAt: new Date(),
    statusHistory: [
      {
        from: null,
        to: "uploaded",
        by: uploaderUserId,
        at: new Date(),
      },
    ],
  });

  await doc.save();

  processDocumentAsync(doc, file, forceReindex);

  return {
    documentId: doc.documentId,
    fileName: doc.fileName,
    documentType: doc.documentType,
    state: doc.state,
    pageCount: doc.pageCount,
    uploadedAt: doc.uploadedAt,
  };
};

// ─── Background processing ──────────────────────────────────
const processDocumentAsync = async (doc, file, forceReindex) => {
  try {
    await updateDocumentState(doc.documentId, "extracting", "system");

    const result = await extractionService.processDocument({
      documentId: doc.documentId,
      filePath: file.path,
      fileName: file.originalname,
      forceReindex,
    });

    if (!result.success) {
      await updateDocumentState(doc.documentId, "failed", "system");
    }
  } catch (err) {
    console.error(`[DocumentService] ${doc.documentId}:`, err.message);
    await updateDocumentState(doc.documentId, "failed", "system");
  }
};

// ─── Helper: update state ───────────────────────────────────
const updateDocumentState = async (documentId, newState, by) => {
  const doc = await Document.findOne({ documentId });
  if (!doc) return;

  await Document.findOneAndUpdate(
    { documentId },
    {
      state: newState,
      $push: {
        statusHistory: {
          from: doc.state,
          to: newState,
          by,
          at: new Date(),
        },
      },
    },
  );
};

// ─── List Documents ─────────────────────────────────────────
const listDocuments = async (filters = {}, pagination = {}) => {
  const { state, documentType, uploaderUserId } = filters;
  const { page = 1, size = 10 } = pagination;

  const query = {};
  if (state) query.state = state;
  if (documentType) query.documentType = documentType;
  if (uploaderUserId) query.uploaderUserId = uploaderUserId;

  const skip = (page - 1) * size;

  const [documents, total] = await Promise.all([
    Document.find(query)
      .sort({ uploadedAt: -1 })
      .skip(skip)
      .limit(Number(size))
      .select("-statusHistory -manualIndexing"),
    Document.countDocuments(query),
  ]);

  return {
    items: documents,
    total,
    page: Number(page),
    size: Number(size),
    totalPages: Math.ceil(total / size),
  };
};

// ─── Get Single Document ────────────────────────────────────
const getDocument = async (documentId) => {
  const doc = await Document.findOne({ documentId });
  if (!doc) throw createError("Document not found", 404, "NOT_FOUND");

  const metadata = await DocumentMetadata.findOne({ documentId });

  return {
    ...doc.toObject(),
    metadata: metadata?.fields || null,
  };
};

// ─── Delete Document ────────────────────────────────────────
const deleteDocument = async (documentId) => {
  const doc = await Document.findOne({ documentId });
  if (!doc) throw createError("Document not found", 404, "NOT_FOUND");

  const javaApiClient = require("./javaApi.client");
  const javaResult = await javaApiClient.removeFromJava(documentId);
  if (!javaResult.success) {
    console.warn(`[DocumentService] Java removal failed for ${documentId}`);
  }

  await storageService.deleteFile(doc.storagePath);
  await DocumentMetadata.deleteOne({ documentId });
  await Document.deleteOne({ documentId });

  return { message: "Document deleted successfully", documentId };
};

// ─── Get Document File ──────────────────────────────────────
const getDocumentFile = async (documentId) => {
  const doc = await Document.findOne({ documentId });
  if (!doc) throw createError("Document not found", 404, "NOT_FOUND");

  if (!storageService.fileExists(doc.storagePath)) {
    throw createError("File not found on storage", 404, "FILE_NOT_FOUND");
  }

  return {
    filePath: storageService.getFilePath(doc.storagePath),
    fileName: doc.fileName,
  };
};

module.exports = {
  uploadDocument,
  listDocuments,
  getDocument,
  deleteDocument,
  getDocumentFile,
};
