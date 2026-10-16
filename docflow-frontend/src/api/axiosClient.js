// src/services/api/axiosClient.js

import axios from "axios";

import appConfig from "../config/appConfig";
const axiosClient = axios.create({
  baseURL: appConfig.apiBaseUrl,

  timeout: 30000,

  headers: {
    "Content-Type": "application/json",
  },
});

export default axiosClient;