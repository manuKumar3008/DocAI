import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";

const formatDate = (date) => {
  if (!date) return "-";

  return new Date(date).toLocaleDateString(
    "en-IN",
    {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }
  );
};

export const getDocumentLibraryColumns = ({
  selectedDocuments,
  onToggleSelection,
  onToggleAll,
  documents,
}) => [
    {
      field: "select",
      headerName: "",

      width: 60,

      renderHeader: () => (
        <Checkbox
          checked={
            documents.length > 0 &&
            selectedDocuments.length ===
            documents.length
          }
          indeterminate={
            selectedDocuments.length > 0 &&
            selectedDocuments.length <
            documents.length
          }
          onChange={(event) =>
            onToggleAll(event.target.checked)
          }
        />
      ),

      renderCell: (row) => (
        <Checkbox
          checked={selectedDocuments.some(
            (item) =>
              item.documentId ===
              row.documentId
          )}
          onChange={() =>
            onToggleSelection(row)
          }
        />
      ),
    },

    {
      field: "fileName",

      headerName: "Document Name",

      flex: 2,
    },

    {
      field: "documentType",

      headerName: "Document Type",

      flex: 1,

      renderCell: (row) =>
        row.documentType || "-",
    },



    {
      field: "state",

      headerName: "Status",

      width: 150,

      renderCell: () => (
        <Chip
          size="small"
          color="success"
          label="INDEXED"
        />
      ),
    },


  ];