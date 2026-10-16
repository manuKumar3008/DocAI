import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
} from "@mui/material";

import RefreshIcon from "@mui/icons-material/Refresh";

function DashboardToolbar({
  status,
  documentType,
  loading,
  onStatusChange,
  onDocumentTypeChange,
  onRefresh,
}) {
  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: 2,
        mb: 3,
      }}
    >
      <Box
        sx={{
          display: "flex",
          gap: 2,
          flexWrap: "wrap",
        }}
      >
        <FormControl size="small" sx={{ minWidth: 220 }}>
          <InputLabel>Document Type</InputLabel>

          <Select
            value={documentType}
            label="Document Type"
            onChange={(e) =>
              onDocumentTypeChange(e.target.value)
            }
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="POLICY_DOCUMENTS">
              Policy Documents
            </MenuItem>
            <MenuItem value="INVOICE">
              Invoice
            </MenuItem>
            <MenuItem value="CONTRACT">
              Contract
            </MenuItem>
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel>Status</InputLabel>

          <Select
            value={status}
            label="Status"
            onChange={(e) =>
              onStatusChange(e.target.value)
            }
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="indexed">
              Indexed
            </MenuItem>
            <MenuItem value="processing">
              Processing
            </MenuItem>
            <MenuItem value="failed">
              Failed
            </MenuItem>
          </Select>
        </FormControl>
      </Box>

      <Button
        variant="outlined"
        startIcon={<RefreshIcon />}
        onClick={onRefresh}
        disabled={loading}
      >
        Refresh
      </Button>
    </Box>
  );
}

export default DashboardToolbar;