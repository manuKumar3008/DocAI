import apiClient from "../../../api";

const uploadService = {
  upload(formData) {
    return apiClient.post(
      "/api/documents",
      formData,
      {
        headers: {
          "Content-Type":
            "multipart/form-data",
        },
      }
    );
  },
};

export default uploadService;