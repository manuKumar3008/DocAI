import apiClient from "../../../api";
import { API_ENDPOINTS } from "../../../api/apiEndpoints";

const authService = {
  async login(credentials) {
     ////console.log("STEP 3 - Before API Call");
    const response = await apiClient.post(
      API_ENDPOINTS.AUTH.LOGIN,
      credentials
    );
    ////console.log("STEP 4 - API Response", response);

    return response.data.data;
  },

  async logout() {
    return await apiClient.post(API_ENDPOINTS.AUTH.LOGOUT);
  },
};

export default authService;