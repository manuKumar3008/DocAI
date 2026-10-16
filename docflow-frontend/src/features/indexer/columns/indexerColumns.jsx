import Chip from "@mui/material/Chip";

import IndexerActions from "../components/IndexerActions";

const formatDate = (date) =>
  new Date(date).toLocaleDateString(
    "en-IN",
    {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }
  );

const getStateColor = (state) => {
  switch (state?.toLowerCase()) {
    case "pending_review":
      return "warning";

    case "indexed":
      return "success";

    case "rejected":
      return "error";

    default:
      return "default";
  }
};

export const getIndexerColumns = ({
  onReview,
  onReject,
}) => [
  {
    field: "fileName",
    headerName: "Document Name",
  },

  {
    field: "documentType",
    headerName: "Document Type",

    renderCell: (row) =>
      row.documentType || "-",
  },
  {
    field: "uploadedAt",
    headerName: "Uploaded",

    renderCell: (row) =>
      formatDate(row.uploadedAt),
  },

  {
    field: "state",
    headerName: "Status",

    renderCell: (row) => (
      <Chip
        size="small"
        label={row.state
          ?.replaceAll("_", " ")
          .toUpperCase()}
        color={getStateColor(
          row.state
        )}
      />
    ),
  },

  {
    field: "actions",
    headerName: "",
    align: "right",

    renderCell: (row) => (
      <IndexerActions
        document={row}
        onReview={onReview}
        onReject={onReject}
      />
    ),
  },
];