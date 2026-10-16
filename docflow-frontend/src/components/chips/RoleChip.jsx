// src/components/chips/RoleChip.jsx

import { Chip } from "@mui/material";

const roleConfig = {
  ADMIN: {
    label: "ADMIN",
    bg: "#FEE2E2",
    color: "#B91C1C",
  },
  INDEXER: {
    label: "INDEXER",
    bg: "#DBEAFE",
    color: "#1D4ED8",
  },
  PROCESSOR: {
    label: "PROCESSOR",
    bg: "#DCFCE7",
    color: "#15803D",
  },
};

function RoleChip({ role }) {
  const config = roleConfig[role] || {
    label: role,
    bg: "#F3F4F6",
    color: "#374151",
  };

  return (
    <Chip
      label={config.label}
      size="small"
      sx={{
        minWidth: 95,
        fontWeight: 600,
        fontSize: 12,
        bgcolor: config.bg,
        color: config.color,
        borderRadius: "999px",
      }}
    />
  );
}

export default RoleChip;