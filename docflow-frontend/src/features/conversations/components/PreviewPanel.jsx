import {
  Box,
  Divider,
  IconButton,
  Paper,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";

import ChevronRightOutlinedIcon from "@mui/icons-material/ChevronRightOutlined";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";

import apiClient from "../../../api";

function PreviewPanel({
  document,
  previewOpen,
  setPreviewOpen,
}) {
  const [previewUrl, setPreviewUrl] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let objectUrl = null;

    const loadPreview = async () => {
      if (!document?.documentId) {
        setPreviewUrl(null);
        return;
      }

      try {
        setLoading(true);

        const response = await apiClient.get(
          `/api/documents/${document.documentId}/file`,
          {
            responseType: "blob",
          }
        );

        objectUrl = URL.createObjectURL(response.data);
        setPreviewUrl(objectUrl);
      } catch (error) {
        //console.error("Failed to load preview", error);
        setPreviewUrl(null);
      } finally {
        setLoading(false);
      }
    };

    loadPreview();

    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [document]);

  return (
    <Paper
      elevation={0}
      sx={{
        flex: 1,
        height: "100%",
        display: "flex",
        flexDirection: "column",
        minHeight: 0,
        borderRadius: 4,
        border: "1px solid",
        borderColor: "divider",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <Box
        sx={{
          px: 2,
          py: 1.5,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h6" fontWeight={700}>
            Document Preview
          </Typography>

          <Typography
            variant="body2"
            color="text.secondary"
            noWrap
          >
            {document?.documentName ||
              document?.fileName ||
              "No document selected"}
          </Typography>
        </Box>

        <IconButton
          size="small"
          onClick={() => setPreviewOpen(false)}
        >
          <ChevronRightOutlinedIcon />
        </IconButton>
      </Box>

      <Divider />

      {/* PDF */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          bgcolor: "grey.50",
        }}
      >
        {loading ? (
          <Box
            sx={{
              height: "100%",
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
            }}
          >
            <Typography>Loading preview...</Typography>
          </Box>
        ) : previewUrl ? (
          <iframe
            title="Document Preview"
            src={previewUrl}
            style={{
              width: "100%",
              height: "100%",
              border: "none",
            }}
          />
        ) : (
          <Box
            sx={{
              height: "100%",
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              alignItems: "center",
              textAlign: "center",
              px: 3,
            }}
          >
            <DescriptionOutlinedIcon
              sx={{
                fontSize: 72,
                color: "text.disabled",
                mb: 2,
              }}
            />

            <Typography variant="h6">
              No Preview Available
            </Typography>

            <Typography
              variant="body2"
              color="text.secondary"
              mt={1}
            >
              Select a document to preview it.
            </Typography>
          </Box>
        )}
      </Box>
    </Paper>
  );
}

export default PreviewPanel;