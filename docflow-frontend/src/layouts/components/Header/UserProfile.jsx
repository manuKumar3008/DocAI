import {
  Avatar,
  Box,
  Typography,
} from "@mui/material";

import { useAppSelector } from "../../../hooks";
import useResponsive from "../../../hooks/useResponsive";

function UserProfile() {
  const { isDesktop } = useResponsive();

  const { user } = useAppSelector((state) => state.auth);

  const initials = user?.name
    ? user.name
        .split(" ")
        .map((word) => word[0])
        .join("")
        .substring(0, 2)
        .toUpperCase()
    : "U";

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
        cursor: "pointer",
      }}
    >
      <Avatar
        sx={{
          width: 38,
          height: 38,
          bgcolor: "primary.main",
          fontSize: 14,
          fontWeight: 600,
        }}
      >
        {initials}
      </Avatar>

      {isDesktop && (
        <Box>
          <Typography
            variant="body2"
            sx={{
              fontWeight: 600,
              lineHeight: 1.2,
            }}
          >
            {user?.name || "Guest User"}
          </Typography>

          <Typography
            variant="caption"
            color="text.secondary"
          >
            {user?.role || "User"}
          </Typography>
        </Box>
      )}
    </Box>
  );
}

export default UserProfile;