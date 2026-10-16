// src/components/table/TableLayout.jsx

import { Box } from "@mui/material";

function TableLayout({ children }) {
  return (
    <Box
      sx={{
        flex: 1,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        overflow: "auto",
      }}
    >
      {children}
    </Box>
  );
}

export default TableLayout;