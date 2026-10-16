import { useState } from "react";

import {
  IconButton,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Tooltip,
} from "@mui/material";

import MoreVertIcon from "@mui/icons-material/MoreVert";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import LockResetOutlinedIcon from "@mui/icons-material/LockResetOutlined";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import ToggleOnOutlinedIcon from "@mui/icons-material/ToggleOnOutlined";
import ToggleOffOutlinedIcon from "@mui/icons-material/ToggleOffOutlined";

function UserActions({
  user,
  onEdit,
  onDelete,
  onResetPassword,
  onToggleStatus,
}) {
  const [anchorEl, setAnchorEl] = useState(null);

  const open = Boolean(anchorEl);

  const handleOpen = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <>
      <Tooltip title="Actions">
        <IconButton
          size="small"
          onClick={handleOpen}
        >
          <MoreVertIcon fontSize="small" />
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
      >
        <MenuItem
          onClick={() => {
            handleClose();
            onEdit?.(user);
          }}
        >
          <ListItemIcon>
            <EditOutlinedIcon fontSize="small" />
          </ListItemIcon>

          <ListItemText>
            Edit
          </ListItemText>
        </MenuItem>

        <MenuItem
          onClick={() => {
            handleClose();
            onResetPassword?.(user);
          }}
        >
          <ListItemIcon>
            <LockResetOutlinedIcon fontSize="small" />
          </ListItemIcon>

          <ListItemText>
            Reset Password
          </ListItemText>
        </MenuItem>

        <MenuItem
          onClick={() => {
            handleClose();
            onToggleStatus?.(user);
          }}
        >
          <ListItemIcon>
            {user.active ? (
              <ToggleOffOutlinedIcon fontSize="small" />
            ) : (
              <ToggleOnOutlinedIcon fontSize="small" />
            )}
          </ListItemIcon>

          <ListItemText>
            {user.active
              ? "Deactivate"
              : "Activate"}
          </ListItemText>
        </MenuItem>

        <MenuItem
          onClick={() => {
            handleClose();
            onDelete?.(user);
          }}
          sx={{
            color: "error.main",
          }}
        >
          <ListItemIcon
            sx={{
              color: "error.main",
            }}
          >
            <DeleteOutlineOutlinedIcon fontSize="small" />
          </ListItemIcon>

          <ListItemText>
            Delete
          </ListItemText>
        </MenuItem>
      </Menu>
    </>
  );
}

export default UserActions;