import {
  AppBar,
  Box,
  IconButton,
  Toolbar,
} from "@mui/material";

import MenuOutlinedIcon from "@mui/icons-material/MenuOutlined";

import { LAYOUT } from "../../layout.constants";
import { useSidebar } from "../../../contexts/SidebarContext";
import useResponsive from "../../../hooks/useResponsive";

import Logo from "./Logo";
import Notification from "./Notifications";
import UserProfile from "./UserProfile";

function Header() {
  const { toggleSidebar } = useSidebar();

  const { isDrawer } = useResponsive();

  return (
    <AppBar
      position="fixed"
      elevation={0}
      color="inherit"
      sx={{
        height: LAYOUT.HEADER_HEIGHT,
        bgcolor: "background.paper",
        borderBottom: 1,
        borderColor: "divider",
        zIndex: (theme) => theme.zIndex.drawer + 1,
      }}
    >
      <Toolbar
        sx={{
          minHeight: `${LAYOUT.HEADER_HEIGHT}px !important`,
          px: {
            xs: 1.5,
            sm: 2,
            md: 3,
          },
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        {/* Left Section */}
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1.5,
          }}
        >
          {isDrawer && (
            <IconButton
              edge="start"
              onClick={toggleSidebar}
              sx={{
                mr: 0.5,
              }}
            >
              <MenuOutlinedIcon />
            </IconButton>
          )}

          <Logo />
        </Box>

        {/* Right Section */}
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: {
              xs: 0.5,
              sm: 1,
              md: 2,
            },
          }}
        >
          <Notification />

          <UserProfile />
        </Box>
      </Toolbar>
    </AppBar>
  );
}

export default Header;