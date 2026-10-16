// src/layouts/components/Sidebar/SidebarHeader.jsx

import {
  Box,
  IconButton,
  Typography,
} from "@mui/material";

import MenuOpenIcon from "@mui/icons-material/MenuOpen";
import MenuIcon from "@mui/icons-material/Menu";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";

import { useSidebar } from "../../../contexts/SidebarContext";

function SidebarHeader() {
  const {
    collapsed,
    toggleCollapsed,
  } = useSidebar();

  return (
    <Box
      sx={{
        height: 72,
        px: collapsed ? 1 : 2,
        display: "flex",
        alignItems: "center",
        justifyContent: collapsed
          ? "center"
          : "space-between",
        borderBottom: "1px solid",
        borderColor: "divider",
        position: "relative",
      }}
    >
      {!collapsed ? (
        <>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1.5,
            }}
          >
            <AutoAwesomeIcon
              color="primary"
              sx={{
                fontSize: 28,
              }}
            />

            <Typography
              variant="h6"
              fontWeight={700}
              noWrap
            >
              DocFlow AI
            </Typography>
          </Box>

          <IconButton
            size="small"
            onClick={toggleCollapsed}
          >
            <MenuOpenIcon />
          </IconButton>
        </>
      ) : (
        <IconButton
          onClick={toggleCollapsed}
          sx={{
            width: 48,
            height: 48,
          }}
        >
          <MenuIcon />
        </IconButton>
      )}
    </Box>
  );
}

export default SidebarHeader;