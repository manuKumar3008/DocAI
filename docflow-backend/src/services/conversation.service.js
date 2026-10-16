const { Conversation, Message, Document } = require("../models");
const { createError } = require("../middleware/error.middleware");
const { generateConversationId } = require("../utils/generateId");
const javaApiClient = require("./javaApi.client");

// ─── Parse SSE response to clean raw payload ─────────────────
const parseSSEResponse = (rawResponse) => {
  if (!rawResponse) return "";

  const lines = rawResponse.split("\n");
  const contentLines = [];

  for (const line of lines) {
    if (!line.startsWith("data:")) continue;

    const content = line.replace(/^data:\s*/, "").trim();

    if (
      !content ||
      content === "[DONE]" ||
      content === "connected" ||
      content === "Connection closed" ||
      content.startsWith("Connected:") ||
      content.startsWith("Connection:")
    ) {
      continue;
    }

    contentLines.push(content);
  }

  return contentLines.join("").trim();
};

const parseQnaPayload = (rawText) => {
  if (!rawText) {
    return { answer: "", references: [], suggestedFollowUps: [] };
  }

  try {
    const parsed = JSON.parse(rawText);
    return {
      answer: parsed?.answer || "",
      references: Array.isArray(parsed?.references) ? parsed.references : [],
      suggestedFollowUps: Array.isArray(parsed?.suggestedFollowUps)
        ? parsed.suggestedFollowUps
        : [],
    };
  } catch (err) {
    return {
      answer: rawText,
      references: [],
      suggestedFollowUps: [],
    };
  }
};

// ─── Get next sequence number for messages ──────────────────
const getNextSequence = async (conversationId) => {
  const last = await Message.findOne({ conversationId })
    .sort({ sequence: -1 })
    .select("sequence");
  return (last?.sequence || 0) + 1;
};

// ─── Create Conversation ────────────────────────────────────
const createConversation = async ({ documentIds, title, createdBy }) => {
  if (!documentIds || documentIds.length === 0) {
    throw createError(
      "At least one documentId is required",
      400,
      "VALIDATION_ERROR",
    );
  }

  const docs = await Document.find({
    documentId: { $in: documentIds },
    state: "indexed",
  });

  if (docs.length !== documentIds.length) {
    const foundIds = docs.map((d) => d.documentId);
    const notFound = documentIds.filter((id) => !foundIds.includes(id));
    throw createError(
      `Documents not found or not indexed: ${notFound.join(", ")}`,
      400,
      "DOCUMENTS_NOT_INDEXED",
    );
  }

  const conversation = new Conversation({
    conversationId: generateConversationId(),
    title: title || `Conversation about ${docs[0].fileName}`,
    documentIds,
    createdBy,
    lastActivityAt: new Date(),
  });

  await conversation.save();
  return conversation;
};

// ─── List Conversations ─────────────────────────────────────
const listConversations = async (filters = {}, pagination = {}) => {
  const { createdBy } = filters;
  const { page = 1, size = 10 } = pagination;

  const query = {};
  if (createdBy) query.createdBy = createdBy;

  const skip = (page - 1) * size;

  const [items, total] = await Promise.all([
    Conversation.find(query)
      .sort({ lastActivityAt: -1 })
      .skip(skip)
      .limit(Number(size)),
    Conversation.countDocuments(query),
  ]);

  return {
    items,
    total,
    page: Number(page),
    size: Number(size),
    totalPages: Math.ceil(total / size),
  };
};

// ─── Get Single Conversation ────────────────────────────────
const getConversation = async (conversationId) => {
  const conversation = await Conversation.findOne({ conversationId });
  if (!conversation)
    throw createError("Conversation not found", 404, "NOT_FOUND");
  return conversation;
};

// ─── Ask Question — Sync (structured JSON) ──────────────────
const askSync = async (conversationId, question, userId) => {
  const conversation = await Conversation.findOne({ conversationId });
  if (!conversation)
    throw createError("Conversation not found", 404, "NOT_FOUND");

  if (!question || !question.trim()) {
    throw createError("Question is required", 400, "VALIDATION_ERROR");
  }

  const userSeq = await getNextSequence(conversationId);
  await Message.create({
    conversationId,
    sequence: userSeq,
    role: "user",
    content: question,
    references: [],
    createdAt: new Date(),
  });

  const javaResult = await javaApiClient.askQuestion(
    question,
    conversation.documentIds,
    conversationId,
  );

  if (!javaResult.success) {
    throw createError("Failed to get answer from AI engine", 500, "QNA_FAILED");
  }

  const payload =
    typeof javaResult.data === "string"
      ? parseQnaPayload(javaResult.data)
      : {
          answer: javaResult.data?.answer || javaResult.data?.response || "",
          references: Array.isArray(javaResult.data?.references)
            ? javaResult.data.references
            : [],
          suggestedFollowUps: Array.isArray(javaResult.data?.suggestedFollowUps)
            ? javaResult.data.suggestedFollowUps
            : [],
        };

  const { answer, references, suggestedFollowUps } = payload;

  if (!answer) {
    throw createError("No answer received from AI engine", 500, "QNA_FAILED");
  }
  const assistantSeq = await getNextSequence(conversationId);
  await Message.create({
    conversationId,
    sequence: assistantSeq,
    role: "assistant",
    content: answer,
    references,
    suggestedFollowUps,
    createdAt: new Date(),
  });

  await Conversation.findOneAndUpdate(
    { conversationId },
    { lastActivityAt: new Date() },
  );

  return {
    conversationId,
    question,
    answer,
    references,
    suggestedFollowUps,
    sequence: assistantSeq,
  };
};

// ─── Ask Question — SSE Stream ──────────────────────────────
const askStream = async (conversationId, question, userId, res) => {
  const conversation = await Conversation.findOne({ conversationId });
  if (!conversation)
    throw createError("Conversation not found", 404, "NOT_FOUND");

  if (!question || !question.trim()) {
    throw createError("Question is required", 400, "VALIDATION_ERROR");
  }

  const userSeq = await getNextSequence(conversationId);
  await Message.create({
    conversationId,
    sequence: userSeq,
    role: "user",
    content: question,
    references: [],
    createdAt: new Date(),
  });

  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.flushHeaders();

  let fullPayload = "";

  try {
    await javaApiClient.streamQuestion(
      question,
      conversation.documentIds,
      conversationId,
      res,
      (token) => {
        fullPayload += token;
      },
    );
  } catch (err) {
    console.error("[ConversationService] Stream error:", err.message);
  }

  if (fullPayload) {
    const rawPayload = parseSSEResponse(fullPayload) || fullPayload;
    const { answer, references } = parseQnaPayload(rawPayload);

    const assistantSeq = await getNextSequence(conversationId);
    await Message.create({
      conversationId,
      sequence: assistantSeq,
      role: "assistant",
      content: answer || rawPayload,
      references,
      createdAt: new Date(),
    });

    await Conversation.findOneAndUpdate(
      { conversationId },
      { lastActivityAt: new Date() },
    );
  }
};

// ─── Get Messages ───────────────────────────────────────────
const getMessages = async (conversationId, pagination = {}) => {
  const conversation = await Conversation.findOne({ conversationId });
  if (!conversation)
    throw createError("Conversation not found", 404, "NOT_FOUND");

  const { page = 1, size = 20 } = pagination;
  const skip = (page - 1) * size;

  const [messages, total] = await Promise.all([
    Message.find({ conversationId })
      .sort({ sequence: 1 })
      .skip(skip)
      .limit(Number(size)),
    Message.countDocuments({ conversationId }),
  ]);

  return {
    conversationId,
    documentIds: conversation.documentIds || [],
    messages,
    total,
    page: Number(page),
    size: Number(size),
    totalPages: Math.ceil(total / size),
  };
};

module.exports = {
  createConversation,
  listConversations,
  getConversation,
  askSync,
  askStream,
  getMessages,
};
