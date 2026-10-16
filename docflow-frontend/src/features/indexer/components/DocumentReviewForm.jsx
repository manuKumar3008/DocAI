// src/features/indexer/components/DocumentReviewForm.jsx

import {
  Box,
  Divider,
  Typography,
} from "@mui/material";

import { useWatch } from "react-hook-form";

import {
  SelectInput,
} from "../../../components/common/form";

import DynamicMetadataForm from "./DynamicMetadataForm";

function DocumentReviewForm({
  control,
  document,
  documentTypes = [],
}) {
  const selectedDocumentType = useWatch({
    control,
    name: "documentType",
  });

  const selectedType =
    documentTypes.find(
      (item) =>
        item.documentType ===
        selectedDocumentType
    ) || null;

  // Debug Logs (Remove after verification)
 

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 3,
      }}
    >
      {/* Document Information */}
      {document && (
        <>
          <Box>
            <Typography
              variant="subtitle2"
              color="text.secondary"
            >
              Document Name
            </Typography>

            <Typography
              fontWeight={600}
            >
              {document.fileName}
            </Typography>
          </Box>

          <Box>
            <Typography
              variant="subtitle2"
              color="text.secondary"
            >
              Document ID
            </Typography>

            <Typography>
              {document.documentId}
            </Typography>
          </Box>

          <Divider />
        </>
      )}

      {/* Document Type */}
      <SelectInput
        control={control}
        name="documentType"
        label="Document Type"
        options={documentTypes.map(
          (item) => ({
            label: item.displayName,
            value: item.documentType,
          })
        )}
      />

      {/* Dynamic Fields */}
      {selectedType && (
        <DynamicMetadataForm
          control={control}
          mandatoryFields={
            selectedType.mandatoryFields
          }
          optionalFields={
            selectedType.optionalFields
          }
        />
      )}
    </Box>
  );
}

export default DocumentReviewForm;