// src/layouts/components/Workspace/ContentArea.jsx

import { Box } from "@mui/material";
import { Outlet } from "react-router-dom";
import { LAYOUT } from "../../layout.constants";

function ContentArea() {
  return (
    <Box
      sx={{
        flex: 1,
        display: "flex",
        flexDirection: "column",

        minHeight: 0,

        overflow: "auto",

        p: LAYOUT.CONTENT_PADDING,
      }}
    >
      <Outlet />
    </Box>
  );
}

export default ContentArea;