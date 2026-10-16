import apiClient from "../../../api";

/**
 * Get all document types
 */
export const getDocumentTypes = async (params = {}) => {
  const response = await apiClient.get(
    "/api/admin/document-types",
    {
      params,
    }
  );

  return response.data;
};

/**
 * Create a new document type
 */
export const createDocumentType = async (payload) => {
  const response = await apiClient.post(
    "/api/admin/document-types",
    payload
  );

  return response.data;
};

/**
 * Update document type
 * Used for:
 * - Edit document type
 * - Activate
 * - Deactivate
 */
export const updateDocumentType = async (
  documentType,
  payload
) => {
  const response = await apiClient.put(
    `/api/admin/document-types/${documentType}`,
    payload
  );

  return response.data;
};

/**
 * Delete document type
 */
export const deleteDocumentType = async (
  documentType
) => {
  const response = await apiClient.delete(
    `/api/admin/document-types/${documentType}`
  );

  return response.data;
};

/**
 * Get confidence threshold
 */
export const getConfidenceThreshold = async () => {
  const response = await apiClient.get(
    "/api/admin/confidence-threshold"
  );

  return response.data;
};

/**
 * Update confidence threshold
 */
export const updateConfidenceThreshold = async (
  payload
) => {
  const response = await apiClient.put(
    "/api/admin/confidence-threshold",
    payload
  );

  return response.data;
};