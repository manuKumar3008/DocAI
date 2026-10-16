import { Chip } from "@mui/material";

function StatusChip({ status }) {
  const config = {
    indexed: {
      label: "Indexed",
      color: "success",
    },
    processing: {
      label: "Processing",
      color: "warning",
    },
    failed: {
      label: "Failed",
      color: "error",
    },
  };

  const item =
    config[status] || {
      label: status,
      color: "default",
    };

  return (
    <Chip
      size="small"
      label={item.label}
      color={item.color}
    />
  );
}

export default StatusChip;