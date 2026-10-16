// src/services/api/apiEndpoints.js

export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: "/api/auth/login",
    LOGOUT: "/api/auth/logout",
  },

  ADMIN: {
    USERS: "/api/admin/users",
  },

  DOCUMENTS: {
    LIST: "/api/documents",
    UPLOAD: "/api/documents/upload",
  },

  INDEXER: {
    BASE: "/api/indexer",
  },

  CONVERSATIONS: {
    BASE: "/api/conversations",
  },

  SETTINGS: {
    BASE: "/api/settings",
  },
};