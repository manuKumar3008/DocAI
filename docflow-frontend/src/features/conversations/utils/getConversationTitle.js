export const getConversationTitle = (
  messages,
  maxLength = 45
) => {
  const firstUserMessage = messages.find(
    (message) => message.role === "user"
  );

  if (!firstUserMessage) {
    return {
      fullTitle: "New Conversation",
      displayTitle: "New Conversation",
    };
  }

  const fullTitle = firstUserMessage.content.trim();

  const displayTitle =
    fullTitle.length > maxLength
      ? `${fullTitle.substring(0, maxLength).trim()}...`
      : fullTitle;

  return {
    fullTitle,
    displayTitle,
  };
};