// src/components/chips/StatusChip.jsx

import { Chip } from "@mui/material";

const statusConfig = {
  ACTIVE: {
    bg: "#DCFCE7",
    color: "#15803D",
  },
  INACTIVE: {
    bg: "#FEE2E2",
    color: "#B91C1C",
  },
};

function StatusChip({ status }) {
  const config =
    statusConfig[status] || {
      bg: "#F3F4F6",
      color: "#374151",
    };

  return (
    <Chip
      label={status}
      size="small"
      sx={{
        minWidth: 80,
        fontWeight: 600,
        fontSize: 12,
        bgcolor: config.bg,
        color: config.color,
        borderRadius: "999px",
      }}
    />
  );
}

export default StatusChip;