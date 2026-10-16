import StatusChip from "../../../components/chips/StatusChip";
import DocumentTypeActionMenu from "../components/DocumentTypeActionMenu";

export const getDocumentTypeColumns = ({
  onEdit,
  onToggleStatus,
  onDelete,
}) => [
  {
    field: "documentType",
    headerName: "Document Type",
  },
  {
    field: "displayName",
    headerName: "Display Name",
  },
  {
    field: "mandatoryFields",
    headerName: "Mandatory Fields",
    renderCell: (row) => row.mandatoryFields?.length ?? 0,
  },
  {
    field: "optionalFields",
    headerName: "Optional Fields",
    renderCell: (row) => row.optionalFields?.length ?? 0,
  },
  {
    field: "active",
    headerName: "Status",
    renderCell: (row) => (
      <StatusChip
        label={row.active ? "ACTIVE" : "INACTIVE"}
        status={row.active ? "ACTIVE" : "INACTIVE"}
      />
    ),
  },
  {
    field: "actions",
    headerName: "",
    renderCell: (row) => (
      <DocumentTypeActionMenu
        row={row}
        onEdit={onEdit}
        onToggleStatus={onToggleStatus}
        onDelete={onDelete}
      />
    ),
  },
];