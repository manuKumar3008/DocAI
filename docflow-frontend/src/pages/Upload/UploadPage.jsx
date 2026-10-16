// src/pages/Upload/UploadPage.jsx

import { useState } from "react";

import {
  Alert,
  Box,
  Button,
  Paper,
  Snackbar,
} from "@mui/material";

import CloudUploadOutlinedIcon from "@mui/icons-material/CloudUploadOutlined";

import {
  useAppDispatch,
  useAppSelector,
} from "../../hooks";

import {
  PageHeader,
  PageLayout,
} from "../../components/page";

import UploadDropzone from "../../features/upload/components/UploadDropzone";
import ForceReindexToggle from "../../features/upload/components/ForceReindexToggle";

import {
  uploadDocument,
} from "../../features/upload/thunk/uploadThunk";

function UploadPage() {
  const dispatch = useAppDispatch();

  const { uploading } = useAppSelector(
    (state) => state.upload
  );

  const [file, setFile] = useState(null);

  const [forceReindex, setForceReindex] =
    useState(false);

  const [snackbar, setSnackbar] =
    useState({
      open: false,
      severity: "success",
      message: "",
    });

  const handleUpload = async () => {
    if (!file) return;

    const result = await dispatch(
      uploadDocument({
        file,
        forceReindex,
      })
    );

    if (
      uploadDocument.fulfilled.match(result)
    ) {
      setSnackbar({
        open: true,
        severity: "success",
        message:
          "Document uploaded successfully.",
      });

      setFile(null);
      setForceReindex(false);
    } else {
      setSnackbar({
        open: true,
        severity: "error",
        message:
          result.payload ||
          "Upload failed.",
      });
    }
  };

  return (
    <PageLayout>
      <PageHeader
        title="Upload Documents"
        subtitle="Upload documents for AI indexing."
      />

      <Paper
        sx={{
          p: 4,
          borderRadius: 3,
        }}
      >
        <UploadDropzone
          file={file}
          onFileChange={setFile}
          onRemove={() => setFile(null)}
        />

        <ForceReindexToggle
          checked={forceReindex}
          onChange={setForceReindex}
        />

        <Box
          sx={{
            mt: 4,
            display: "flex",
            justifyContent: "flex-end",
          }}
        >
          <Button
            variant="contained"
            size="large"
            startIcon={
              <CloudUploadOutlinedIcon />
            }
            disabled={
              !file || uploading
            }
            onClick={handleUpload}
          >
            {uploading
              ? "Uploading..."
              : "Upload Document"}
          </Button>
        </Box>
      </Paper>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() =>
          setSnackbar((prev) => ({
            ...prev,
            open: false,
          }))
        }
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
      >
        <Alert
          severity={snackbar.severity}
          variant="filled"
          onClose={() =>
            setSnackbar((prev) => ({
              ...prev,
              open: false,
            }))
          }
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </PageLayout>
  );
}

export default UploadPage;