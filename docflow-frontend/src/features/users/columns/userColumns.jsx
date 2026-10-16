import RoleChip from "../../../components/chips/RoleChip";
import StatusChip from "../../../components/chips/StatusChip";
import UserActions from "../components/UserActions";

const formatDate = (date) =>
  new Date(date).toLocaleDateString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });

export const getUserColumns = ({
  onEdit,
  onDelete,
  onResetPassword,
  onToggleStatus,
}) => [
  {
    field: "name",
    headerName: "Name",
  },
  {
    field: "email",
    headerName: "Email",
  },
  {
    field: "role",
    headerName: "Role",
    renderCell: (row) => (
      <RoleChip role={row.role} />
    ),
  },
  {
    field: "active",
    headerName: "Status",
    renderCell: (row) => (
      <StatusChip
        status={row.active ? "ACTIVE" : "INACTIVE"}
      />
    ),
  },
  {
    field: "createdAt",
    headerName: "Created",
    renderCell: (row) => formatDate(row.createdAt),
  },
  {
    field: "actions",
    headerName: "",
    renderCell: (row) => (
      <UserActions
        user={row}
        onEdit={onEdit}
        onDelete={onDelete}
        onResetPassword={onResetPassword}
        onToggleStatus={onToggleStatus}
      />
    ),
  },
];