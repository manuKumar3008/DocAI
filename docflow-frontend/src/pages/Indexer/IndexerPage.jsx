import { useEffect } from "react";
import { useForm } from "react-hook-form";

import { Button, Box } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";

import {
  useAppDispatch,
  useAppSelector,
} from "../../hooks";

import {
  fetchQueue,
  fetchDocumentTypes,
  submitReview,
} from "../../features/indexer/thunk/indexerThunk";

import {
  openDialog,
  closeDialog,
} from "../../features/indexer/slice/indexerSlice";

import {
  PageLayout,
  PageHeader,
  PageToolbar,
} from "../../components/page";

import {
  AppTable,
  TableLayout,
} from "../../components/table";

import {
  AppDialog,
} from "../../components/dialog";

import {
  getIndexerColumns,
} from "../../features/indexer/columns/indexerColumns";

import DocumentReviewForm from "../../features/indexer/components/DocumentReviewForm";

function IndexerPage() {
  const dispatch = useAppDispatch();

  const {
    queue,
    loading,
    dialog,
    selectedDocument,
    documentTypes,
    page,
    size,
    total,
  } = useAppSelector(
    (state) => state.indexer
  );

  const {
    control,
    handleSubmit,
    reset,
  } = useForm({
    defaultValues: {
      documentType: "",
    },
  });

  useEffect(() => {
    dispatch(
      fetchQueue({
        page: 1,
        size,
      })
    );

    dispatch(fetchDocumentTypes());
  }, [dispatch]);

  const handleRefresh = () => {
    dispatch(
      fetchQueue({
        page: page + 1,
        size,
      })
    );
  };

  const handlePageChange = (
    event,
    newPage
  ) => {
    dispatch(
      fetchQueue({
        page: newPage + 1,
        size,
      })
    );
  };

  const handleRowsPerPageChange = (
    event
  ) => {
    const newSize = Number(
      event.target.value
    );

    dispatch(
      fetchQueue({
        page: 1,
        size: newSize,
      })
    );
  };

  const handleReview = (document) => {
    reset({
      documentType:
        document?.documentType ?? "",

      ...(document?.extractedFields ?? {}),
    });

    dispatch(openDialog(document));
  };

  const handleClose = () => {
    dispatch(closeDialog());

    reset({
      documentType: "",
    });
  };

  const onSubmit = async (data) => {
    if (!selectedDocument) return;

    const metadata = { ...data };

    delete metadata.documentType;

    await dispatch(
      submitReview({
        documentId:
          selectedDocument.documentId,

        payload: {
          action: "index",
          documentType:
            data.documentType,
          metadata,
        },
      })
    );

    dispatch(closeDialog());

    reset({
      documentType: "",
    });

    dispatch(
      fetchQueue({
        page: page + 1,
        size,
      })
    );
  };

  const handleReject = async () => {
    if (!selectedDocument) return;

    await dispatch(
      submitReview({
        documentId:
          selectedDocument.documentId,

        payload: {
          action: "reject",
        },
      })
    );

    dispatch(closeDialog());

    reset({
      documentType: "",
    });

    dispatch(
      fetchQueue({
        page: page + 1,
        size,
      })
    );
  };

  return (
    <PageLayout>
      <PageHeader
        title="Manual Indexing"
        subtitle="Review and index pending documents"
      />
      <Box sx={{ mb: 3 }}>
        <PageToolbar
          right={
            <Button
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={handleRefresh}
              disabled={loading}
            >
              Refresh
            </Button>
          }
        />
      </Box>
      <TableLayout>
        <AppTable
          rows={queue}
          loading={loading}
          columns={getIndexerColumns({
            onReview: handleReview,
          })}
          emptyMessage="No pending documents."
          getRowId={(row) =>
            row.documentId
          }
          pagination={{
            page,
            rowsPerPage: size,
            total,
            onPageChange:
              handlePageChange,
            onRowsPerPageChange:
              handleRowsPerPageChange,
          }}
        />
      </TableLayout>

      <AppDialog
        open={dialog.open}
        title={
          selectedDocument
            ? `Review - ${selectedDocument.fileName}`
            : "Review Document"
        }
        onClose={handleClose}
        maxWidth="md"
        fullWidth
        actions={
          <>
            <Button
              variant="outlined"
              onClick={handleClose}
            >
              Cancel
            </Button>

            <Button
              variant="outlined"
              color="error"
              onClick={handleReject}
              disabled={loading}
            >
              Reject
            </Button>

            <Button
              variant="contained"
              onClick={handleSubmit(
                onSubmit
              )}
              disabled={loading}
            >
              Approve
            </Button>
          </>
        }
      >
        <DocumentReviewForm
          control={control}
          document={
            selectedDocument
          }
          documentTypes={
            documentTypes
          }
        />
      </AppDialog>
    </PageLayout>
  );
}

export default IndexerPage;