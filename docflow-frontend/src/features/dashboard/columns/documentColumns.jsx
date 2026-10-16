import StatusChip from "../../../components/chips/StatusChip";

export const documentColumns = [

  {
    field: "fileName",
    headerName: "File Name",
  },

  {
    field: "documentType",
    headerName: "Document Type",
  },

  {
    field: "state",
    headerName: "Status",

    render: (row) => (
      <StatusChip
        status={row.state}
      />
    ),
  },

  {
    field: "uploadedAt",
    headerName: "Uploaded",

    render: (row) =>
      new Date(
        row.uploadedAt
      ).toLocaleDateString(),
  },
];