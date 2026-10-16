import apiClient from "../../../api";

const conversationService = {
  async createConversation(payload) {
    const response = await apiClient.post(
      "/api/conversations",
      payload
    );

    return response.data.data;
  },

  async getConversations(page = 1, size = 20) {
    const response = await apiClient.get(
      "/api/conversations",
      {
        params: {
          page,
          size,
        },
      }
    );

    return response.data.data;
  },

  async getConversationMessages(
    conversationId,
    page = 1,
    size = 20
  ) {
    const response = await apiClient.get(
      `/api/conversations/${conversationId}/messages`,
      {
        params: {
          page,
          size,
        },
      }
    );

    return response.data.data;
  },

  async getDocument(documentId) {
    const response = await apiClient.get(
      `/api/documents/${documentId}`
    );

    return response.data.data;
  },
};

export default conversationService;