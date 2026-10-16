// src/layouts/components/AppShell/AppShell.jsx

import {
  Box,
  Drawer,
  IconButton,
} from "@mui/material";

import MenuOutlinedIcon from "@mui/icons-material/MenuOutlined";

import Sidebar from "../Sidebar/Sidebar";
import ContentArea from "../Workspace/ContentArea";

import { LAYOUT } from "../../layout.constants";

import { useSidebar } from "../../../contexts/SidebarContext";
import useResponsive from "../../../hooks/useResponsive";
import MobileHeader from "../Header/MobileHeader";

function AppShell() {
  const {
    open,
    closeSidebar,
    collapsed,
    toggleSidebar,
  } = useSidebar();

  const {
    isDrawer,
    isDesktop,
  } = useResponsive();

  const sidebarWidth = isDesktop
    ? collapsed
      ? LAYOUT.SIDEBAR.COLLAPSED_WIDTH
      : LAYOUT.SIDEBAR.DESKTOP_WIDTH
    : LAYOUT.SIDEBAR.TABLET_WIDTH;

  return (
    <Box
      sx={{
        display: "flex",
        height: "100vh",
        overflow: "hidden",
        bgcolor: "background.default",
        position: "relative",
      }}
    >
      {/* Floating Mobile / Tablet Menu Button */}

     

      {/* Desktop Sidebar */}

      <Box
        sx={{
          display: {
            xs: "none",
            lg: "block",
          },

          width: sidebarWidth,
          flexShrink: 0,
          height: "100vh",

          transition: "width .25s ease",
        }}
      >
        <Sidebar />
      </Box>

      {/* Mobile / Tablet Drawer */}

      <Drawer
        open={open && isDrawer}
        onClose={closeSidebar}
        variant="temporary"
        ModalProps={{
          keepMounted: true,
        }}
        sx={{
          display: {
            xs: "block",
            lg: "none",
          },

          "& .MuiDrawer-paper": {
            width:
              LAYOUT.SIDEBAR.TABLET_WIDTH,

            height: "100%",

            boxSizing: "border-box",

            borderRight: "1px solid",

            borderColor: "divider",
          },
        }}
      >
        <Sidebar />
      </Drawer>

      {/* Workspace */}

      <Box
        component="main"
        sx={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          minWidth: 0,
          overflow: "hidden",
        }}
      >
        {isDrawer && <MobileHeader />}

        <ContentArea />
      </Box>
    </Box>
  );
}

export default AppShell;