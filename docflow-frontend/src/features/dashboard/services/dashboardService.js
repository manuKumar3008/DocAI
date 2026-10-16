import apiClient from "../../../api";
import { API_ENDPOINTS } from "../../../api/apiEndpoints";

const dashboardService = {
  async getDocuments({
    page = 0,
    size = 10,
    status = "",
    documentType = "",
  }) {
    const params = {
      page: page + 1, // Backend is 1-based
      size,
    };

    if (status) {
      params.status = status;
    }

    if (documentType) {
      params.documentType = documentType;
    }

    const response = await apiClient.get(
      API_ENDPOINTS.DOCUMENTS.LIST,
      {
        params,
      }
    );

    return response.data.data;
  },
};

export default dashboardService;