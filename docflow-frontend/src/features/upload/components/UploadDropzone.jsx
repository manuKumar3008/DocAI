import { useRef } from "react";

import {
  Box,
  Button,
  Stack,
  Typography,
  Paper,
} from "@mui/material";

import UploadFileOutlinedIcon from "@mui/icons-material/UploadFileOutlined";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";

function UploadDropzone({
  file,
  onFileChange,
  onRemove,
}) {
  const inputRef = useRef(null);

  const handleBrowse = () => {
    inputRef.current?.click();
  };

  const handleFileSelect = (event) => {
    const selectedFile =
      event.target.files?.[0];

    if (selectedFile) {
      onFileChange(selectedFile);
    }
  };

  const handleDrop = (event) => {
    event.preventDefault();

    const selectedFile =
      event.dataTransfer.files?.[0];

    if (selectedFile) {
      onFileChange(selectedFile);
    }
  };

  return (
    <>
      <Paper
        variant="outlined"
        onDragOver={(e) =>
          e.preventDefault()
        }
        onDrop={handleDrop}
        sx={{
          p: 5,
          borderStyle: "dashed",
          borderWidth: 2,
          borderRadius: 3,
          textAlign: "center",
          bgcolor: "background.paper",
        }}
      >
        <UploadFileOutlinedIcon
          color="primary"
          sx={{
            fontSize: 60,
            mb: 2,
          }}
        />

        <Typography
          variant="h6"
          fontWeight={600}
        >
          Drag & Drop your document
        </Typography>

        <Typography
          color="text.secondary"
          sx={{ mt: 1 }}
        >
          or
        </Typography>

        <Button
          variant="contained"
          sx={{ mt: 2 }}
          onClick={handleBrowse}
        >
          Browse File
        </Button>

        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mt: 2 }}
        >
          Supported: PDF, DOCX
        </Typography>

        <input
          hidden
          ref={inputRef}
          type="file"
          accept=".pdf,.doc,.docx"
          onChange={handleFileSelect}
        />
      </Paper>

      {file && (
        <Paper
          variant="outlined"
          sx={{
            mt: 3,
            p: 2,
            borderRadius: 2,
          }}
        >
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="center"
          >
            <Box>
              <Typography fontWeight={600}>
                {file.name}
              </Typography>

              <Typography
                variant="body2"
                color="text.secondary"
              >
                {(
                  file.size /
                  1024 /
                  1024
                ).toFixed(2)}{" "}
                MB
              </Typography>
            </Box>

            <Button
              color="error"
              startIcon={
                <DeleteOutlineOutlinedIcon />
              }
              onClick={onRemove}
            >
              Remove
            </Button>
          </Stack>
        </Paper>
      )}
    </>
  );
}

export default UploadDropzone;