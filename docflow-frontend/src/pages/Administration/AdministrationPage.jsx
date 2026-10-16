import { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import {
  Box,
  Button,
  Typography,
} from "@mui/material";

import RefreshIcon from "@mui/icons-material/Refresh";
import AddIcon from "@mui/icons-material/Add";

import {
  PageLayout,
  PageHeader,
  PageToolbar,
} from "../../components/page";

import {
  AppTable,
  TableLayout,
} from "../../components/table";

import { AppDialog } from "../../components/dialog";

import ConfidenceThresholdCard from "../../features/administration/components/ConfidenceThresholdCard";
import AIQuickActionsCard from "../../features/administration/components/AIQuickActionsCard";
import DocumentTypeForm from "../../features/administration/components/DocumentTypeForm";
import { getDocumentTypeColumns } from "../../features/administration/columns/documentTypeColumns";

import {
  loadConfidenceThreshold,
  updateConfidenceThresholdThunk,
  loadDocumentTypes,
  createDocumentTypeThunk,
  updateDocumentTypeThunk,
  toggleDocumentTypeStatusThunk,
  deleteDocumentTypeThunk,
} from "../../features/administration/thunk/administrationThunk";

import {
  setPage,
  setSize,
  setQuickActions,
} from "../../features/administration/slice/administrationSlice";

const initialFormData = {
  documentType: "",
  displayName: "",
  description: "",
  mandatoryFields: [],
  optionalFields: [],
};

export default function AdministrationPage() {
  const dispatch = useDispatch();

  const {
    confidenceThreshold,
    loadingThreshold,
    savingThreshold,

    documentTypes,
    loadingDocumentTypes,
    savingDocumentType,

    page,
    size,
    total,
  } = useSelector((state) => state.administration);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState("create");
  const [formData, setFormData] = useState(initialFormData);

  useEffect(() => {
    dispatch(loadConfidenceThreshold());

    dispatch(
      loadDocumentTypes({
        page: page + 1,
        limit: size,
      })
    );

    const savedQuickActions =
      localStorage.getItem("quickActions");

    if (savedQuickActions) {
      dispatch(
        setQuickActions(
          JSON.parse(savedQuickActions)
        )
      );
    }
  }, [dispatch]);

  const handleThresholdUpdate = (payload) => {
    dispatch(updateConfidenceThresholdThunk(payload));
  };

  const handleRefresh = () => {
    dispatch(
      loadDocumentTypes({
        page: page + 1,
        limit: size,
      })
    );
  };

  const handleCreate = () => {
    setDialogMode("create");
    setFormData(initialFormData);
    setDialogOpen(true);
  };

  const handleEdit = (row) => {
    setDialogMode("edit");

    setFormData({
      documentType: row.documentType,
      displayName: row.displayName,
      description: row.description || "",
      mandatoryFields: row.mandatoryFields || [],
      optionalFields: row.optionalFields || [],
    });

    setDialogOpen(true);
  };

  const handleToggleStatus = async (row) => {
    try {
      await dispatch(
        toggleDocumentTypeStatusThunk({
          documentType: row.documentType,
          payload: {
            ...row,
            active: !row.active,
          },
        })
      ).unwrap();

      handleRefresh();
    } catch (error) {
      //console.error(error);
    }
  };

  const handleDelete = async (row) => {
    try {
      await dispatch(
        deleteDocumentTypeThunk(row.documentType)
      ).unwrap();

      handleRefresh();
    } catch (error) {
      //console.error(error);
    }
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setFormData(initialFormData);
  };

  const handleSave = async () => {
    try {
      if (dialogMode === "create") {
        await dispatch(
          createDocumentTypeThunk(formData)
        ).unwrap();
      } else {
        await dispatch(
          updateDocumentTypeThunk({
            documentType: formData.documentType,
            payload: formData,
          })
        ).unwrap();
      }

      handleCloseDialog();
      handleRefresh();
    } catch (error) {
      //console.error(error);
    }
  };

  const handlePageChange = (_, newPage) => {
    dispatch(setPage(newPage));

    dispatch(
      loadDocumentTypes({
        page: newPage + 1,
        limit: size,
      })
    );
  };

  const handleRowsPerPageChange = (event) => {
    const newSize = parseInt(event.target.value, 10);

    dispatch(setSize(newSize));
    dispatch(setPage(0));

    dispatch(
      loadDocumentTypes({
        page: 1,
        limit: newSize,
      })
    );
  };
  return (
    <PageLayout>
      <PageHeader
        title="Administration"
        subtitle="Manage document types and AI configuration"
      />

      <Box sx={{ mt: 3 }}>
        <ConfidenceThresholdCard
          threshold={confidenceThreshold}
          loading={loadingThreshold}
          saving={savingThreshold}
          onUpdate={handleThresholdUpdate}
        />
      </Box>

      <Box sx={{ mt: 3 }}>
        <AIQuickActionsCard />
      </Box>

      <Box sx={{ mt: 5, mb: 3 }}>
        <PageToolbar
          left={
            <Typography
              variant="h6"
              fontWeight={600}
            >
              Document Types
            </Typography>
          }
          right={
            <>
              <Button
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={handleRefresh}
                disabled={loadingDocumentTypes}
              >
                Refresh
              </Button>

              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={handleCreate}
              >
                Add Document Type
              </Button>
            </>
          }
        />

        <Box sx={{ mt: 3 }}>
          <TableLayout>
            <AppTable
              columns={getDocumentTypeColumns({
                onEdit: handleEdit,
                onToggleStatus: handleToggleStatus,
                onDelete: handleDelete,
              })}
              rows={documentTypes}
              loading={loadingDocumentTypes}
              emptyMessage="No document types found."
              getRowId={(row) => row._id}
              pagination={{
                page,
                rowsPerPage: size,
                total,
                onPageChange: handlePageChange,
                onRowsPerPageChange:
                  handleRowsPerPageChange,
              }}
            />
          </TableLayout>
        </Box>
      </Box>
      <AppDialog
        open={dialogOpen}
        title={
          dialogMode === "create"
            ? "Create Document Type"
            : "Edit Document Type"
        }
        onClose={handleCloseDialog}
        loading={savingDocumentType}
        maxWidth="md"
        actions={
          <>
            <Button
              variant="outlined"
              onClick={handleCloseDialog}
            >
              Cancel
            </Button>

            <Button
              variant="contained"
              onClick={handleSave}
              disabled={savingDocumentType}
            >
              {dialogMode === "create"
                ? "Create"
                : "Update"}
            </Button>
          </>
        }
      >
        <DocumentTypeForm
          mode={dialogMode}
          values={formData}
          onChange={setFormData}
        />
      </AppDialog>
    </PageLayout>
  );
}