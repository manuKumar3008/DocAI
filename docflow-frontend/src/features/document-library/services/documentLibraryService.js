import apiClient from "../../../api";

const documentLibraryService = {
  async getIndexedDocuments({
    page = 0,
    size = 10,
  } = {}) {
    const params = {
      state: "indexed",
      page: page + 1, // Backend is 1-based
      size,
    };

    const response = await apiClient.get(
      "/api/documents",
      {
        params,
      }
    );

    return response.data.data;
  },
};

export default documentLibraryService;