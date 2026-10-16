import { Box, Button, Grid } from "@mui/material";
import { useSelector } from "react-redux";
import { useEffect, useState } from "react";

import VisibilityOutlinedIcon from "@mui/icons-material/VisibilityOutlined";

import ConversationPanel from "./ConversationPanel";
import PreviewPanel from "./PreviewPanel";

function ConversationLayout({ conversationId }) {
  const {
    loading,
    documents,
    messages,
  } = useSelector((state) => state.conversation);

  const [selectedDocument, setSelectedDocument] = useState(null);
  const [previewOpen, setPreviewOpen] = useState(true);

  useEffect(() => {
    if (documents.length > 0) {
      setSelectedDocument(documents[0]);
    } else {
      setSelectedDocument(null);
    }
  }, [conversationId, documents]);
  return (
    <Box
      sx={{
        height: "calc(114vh - 120px)",
        position: "relative",
      }}
    >
      {/* Show Preview Button */}
      {!previewOpen && (
        <Box
          sx={{
            position: "absolute",
            top: 12,
            right: 12,
            zIndex: 10,
          }}
        >
          <Button
            variant="outlined"
            startIcon={<VisibilityOutlinedIcon />}
            onClick={() => setPreviewOpen(true)}
          >
            Show Preview
          </Button>
        </Box>
      )}

      <Grid
        container
        spacing={1.5}
        sx={{
          height: "100%",
          alignItems: "stretch",
        }}
      >
        {/* Conversation */}
        <Grid
          size={{
            xs: 12,
            lg: previewOpen ? 7 : 12, // 60%
          }}
          sx={{
            display: "flex",
            height: "100%",
            minHeight: 0,
          }}
        >
          <ConversationPanel
            conversationId={conversationId}
            loading={loading}
            documents={documents}
            messages={messages}
            selectedDocument={selectedDocument}
            onDocumentSelect={setSelectedDocument}
          />
        </Grid>

        {/* Preview */}
        {previewOpen && (
          <Grid
            size={{
              xs: 12,
              lg: 5, // 40%
            }}
            sx={{
              display: "flex",
              height: "100%",
              minHeight: 0,
            }}
          >
            <PreviewPanel
              document={selectedDocument}
              previewOpen={previewOpen}
              setPreviewOpen={setPreviewOpen}
            />
          </Grid>
        )}
      </Grid>
    </Box>
  );
}

export default ConversationLayout;