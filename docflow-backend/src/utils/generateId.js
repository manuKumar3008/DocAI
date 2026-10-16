const { v4: uuidv4 } = require("uuid");

const generateDocumentId = () =>
  `DOC_${uuidv4().replace(/-/g, "").substring(0, 8).toUpperCase()}`;

const generateConversationId = () =>
  `CONV_${uuidv4().replace(/-/g, "").substring(0, 8).toUpperCase()}`;

const generateUserId = () =>
  `USR_${uuidv4().replace(/-/g, "").substring(0, 8).toUpperCase()}`;

module.exports = { generateDocumentId, generateConversationId, generateUserId };
