import apiClient from "../../../api";

const indexerService = {
  /**
   * Pending Review Queue
   */
  getQueue(params) {
    return apiClient.get(
      "/api/manual-indexing/queue",
      {
        params,
      }
    );
  },

  /**
   * Document Types
   */
  getDocumentTypes() {
    return apiClient.get(
      "/api/admin/document-types"
    );
  },

  /**
   * Approve / Reject
   */
  submitReview(documentId, payload) {
    return apiClient.post(
      `/api/manual-indexing/${documentId}/submit`,
      payload
    );
  },
};

export default indexerService;