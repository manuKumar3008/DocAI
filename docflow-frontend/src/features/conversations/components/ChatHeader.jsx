import { useState } from "react";
import {
  Box,
  Chip,
  Menu,
  MenuItem,
  Stack,
  Typography,
} from "@mui/material";

import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";

function ChatHeader({
  conversationId,
  documents = [],
  selectedDocument,
  onDocumentSelect,
}) {
  const [anchorEl, setAnchorEl] = useState(null);

  const open = Boolean(anchorEl);

  const currentDocument = selectedDocument || documents[0];

  const remainingDocuments = documents.filter(
    (doc) =>
      (doc.documentId || doc.id) !==
      (currentDocument?.documentId || currentDocument?.id)
  );

  const primaryDocumentName =
    currentDocument?.documentName ||
    currentDocument?.fileName ||
    "No document selected";

  const handleOpenDocuments = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleCloseDocuments = () => {
    setAnchorEl(null);
  };

  return (
    <>
      <Box
        sx={{
          px: 3,
          py: 2.5,
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
      >
        {/* Title */}
        <Typography variant="h5" fontWeight={700}>
          Conversation
        </Typography>

        {/* Selected Document */}
        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          mt={2}
        >
          <DescriptionOutlinedIcon
            color="primary"
            fontSize="small"
          />

          <Typography
            variant="subtitle1"
            fontWeight={600}
            noWrap
            sx={{ maxWidth: 450 }}
          >
            {primaryDocumentName}
          </Typography>

          {remainingDocuments.length > 0 && (
            <Chip
              size="small"
              clickable
              label={`+${remainingDocuments.length}`}
              onClick={handleOpenDocuments}
              sx={{
                height: 24,
                borderRadius: 2,
                fontWeight: 600,
              }}
            />
          )}
        </Stack>

        {/* Conversation Info */}
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          mt={2}
        >
          <Box>
            <Typography
              variant="caption"
              color="text.secondary"
            >
              Conversation ID
            </Typography>

            <Typography
              variant="body2"
              sx={{
                fontFamily: "monospace",
                mt: 0.25,
              }}
            >
              {conversationId}
            </Typography>
          </Box>

          <Chip
            size="small"
            color="success"
            label="Active"
            variant="outlined"
          />
        </Stack>
      </Box>

      {/* Remaining Documents */}
      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleCloseDocuments}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
        PaperProps={{
          sx: {
            mt: 0.5,
            minWidth: 320,
            borderRadius: 3,
          },
        }}
      >
        {remainingDocuments.map((document) => (
          <MenuItem
            key={document.documentId || document.id}
            onClick={() => {
              onDocumentSelect(document);
              handleCloseDocuments();
            }}
          >
            <DescriptionOutlinedIcon
              sx={{
                mr: 1.5,
                color: "primary.main",
                fontSize: 20,
              }}
            />

            <Typography
              variant="body2"
              noWrap
              sx={{
                maxWidth: 250,
              }}
            >
              {document.documentName ||
                document.fileName ||
                "Unnamed Document"}
            </Typography>
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

export default ChatHeader;