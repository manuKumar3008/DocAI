import { Badge, IconButton } from "@mui/material";
import NotificationsOutlinedIcon from "@mui/icons-material/NotificationsOutlined";

function Notification() {
  return (
    <IconButton size="medium">
      <Badge
        color="primary"
        variant="dot"
        invisible
      >
        <NotificationsOutlinedIcon />
      </Badge>
    </IconButton>
  );
}

export default Notification;