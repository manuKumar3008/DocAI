// src/services/api/interceptors.js

import axiosClient from "./axiosClient";
import { tokenStorage } from "../utils/tokenStorage";

axiosClient.interceptors.request.use(
  (config) => {
    const token = tokenStorage.getToken();

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      tokenStorage.clear();
    }

    return Promise.reject(error);
  }
);

export default axiosClient;