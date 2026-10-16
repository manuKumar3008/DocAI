import apiClient from "../../../api";
import { API_ENDPOINTS } from "../../../api/apiEndpoints";

const userService = {
  /**
   * Get Users
   */
  async getUsers({
    page = 0,
    size = 10,
    role = "",
  }) {
    const response = await apiClient.get(
      API_ENDPOINTS.ADMIN.USERS,
      {
        params: {
          page: page + 1, // Backend is 1-based
          limit: size,
          ...(role && { role }),
        },
      }
    );

    const result = response.data.data.items;

    return {
      items: result.items,
      total: result.total,
      page: result.page - 1,
      rowsPerPage: result.limit,
      totalPages: result.totalPages,
    };
  },

  /**
   * Create User
   */
  async createUser(payload) {
    const response = await apiClient.post(
      API_ENDPOINTS.ADMIN.USERS,
      payload
    );

    return response.data.data;
  },

  /**
   * Update User
   */
  async updateUser(userId, payload) {
    const response = await apiClient.put(
      `${API_ENDPOINTS.ADMIN.USERS}/${userId}`,
      payload
    );

    return response.data.data;
  },

  /**
   * Delete User
   */
  async deleteUser(userId) {
    const response = await apiClient.delete(
      `${API_ENDPOINTS.ADMIN.USERS}/${userId}`
    );

    return response.data.data;
  },

  /**
   * Reset Password
   */
  async resetPassword(userId, newPassword) {
    const response = await apiClient.put(
      `${API_ENDPOINTS.ADMIN.USERS}/${userId}/reset-password`,
      {
        newPassword,
      }
    );

    return response.data.data;
  },
};

export default userService;