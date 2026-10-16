import {
  Avatar,
  Box,
  Divider,
  Tooltip,
  Typography,
} from "@mui/material";

import LogoutOutlinedIcon from "@mui/icons-material/LogoutOutlined";

import { useNavigate } from "react-router-dom";

import {
  useAppDispatch,
  useAppSelector,
} from "../../../hooks";

import { logout } from "../../../features/auth/thunk/authThunk";
import { ROUTES } from "../../../router";
import { useSidebar } from "../../../contexts/SidebarContext";

function SidebarFooter() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { collapsed } = useSidebar();

  const { user } = useAppSelector(
    (state) => state.auth
  );

  const handleLogout = async () => {
    try {
      await dispatch(logout()).unwrap();

      navigate(ROUTES.LOGIN, {
        replace: true,
      });
    } catch (error) {
      //console.error("Logout failed:", error);

      navigate(ROUTES.LOGIN, {
        replace: true,
      });
    }
  };

  const initials = user?.name
    ? user.name
        .split(" ")
        .map((word) => word[0])
        .join("")
        .substring(0, 2)
        .toUpperCase()
    : "U";

  return (
    <Box>
      <Divider />

      <Box
        sx={{
          p: collapsed ? 1 : 2,
        }}
      >
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: collapsed
              ? "center"
              : "flex-start",
            gap: 2,
          }}
        >
          <Tooltip
            title={user?.name || "Guest User"}
            placement="right"
          >
            <Avatar
              sx={{
                width: 40,
                height: 40,
                bgcolor: "primary.main",
                fontWeight: 600,
              }}
            >
              {initials}
            </Avatar>
          </Tooltip>

          {!collapsed && (
            <Box sx={{ minWidth: 0 }}>
              <Typography
                fontWeight={600}
                noWrap
              >
                {user?.name || "Guest User"}
              </Typography>

              <Typography
                variant="body2"
                color="text.secondary"
                noWrap
              >
                {user?.role || "User"}
              </Typography>
            </Box>
          )}
        </Box>

        <Divider
          sx={{
            my: collapsed ? 1 : 2,
          }}
        />

        <Tooltip
          title="Sign Out"
          placement="right"
        >
          <Box
            onClick={handleLogout}
            sx={{
              display: "flex",
              justifyContent: collapsed
                ? "center"
                : "flex-start",
              alignItems: "center",
              gap: 1,
              p: 1,
              borderRadius: 2,
              cursor: "pointer",
              color: "text.secondary",
              transition: "all .2s ease",

              "&:hover": {
                bgcolor: "action.hover",
                color: "error.main",
              },
            }}
          >
            <LogoutOutlinedIcon fontSize="small" />

            {!collapsed && (
              <Typography fontWeight={500}>
                Sign Out
              </Typography>
            )}
          </Box>
        </Tooltip>
      </Box>
    </Box>
  );
}

export default SidebarFooter;