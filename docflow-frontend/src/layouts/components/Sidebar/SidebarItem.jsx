import {
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
} from "@mui/material";

import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

import { useSidebar } from "../../../contexts/SidebarContext";
import useResponsive from "../../../hooks/useResponsive";

function SidebarItem({
  icon: Icon,
  label,
  active = false,
  onClick,
  expandable = false,
  expanded = false,
  sx = {},
}) {
  const {
    collapsed,
    closeSidebar,
  } = useSidebar();

  const { isDrawer } = useResponsive();

  const handleClick = () => {
    onClick?.();

    // Auto close mobile drawer after navigation
    if (isDrawer) {
      closeSidebar();
    }
  };

  return (
    <Tooltip
      title={collapsed && !isDrawer ? label : ""}
      placement="right"
      arrow
    >
      <ListItemButton
        onClick={handleClick}
        sx={{
          borderRadius: 2,
          mb: 0.5,
          minHeight: 48,
          px: collapsed && !isDrawer ? 1.5 : 2,
          py: 1.1,

          justifyContent:
            collapsed && !isDrawer
              ? "center"
              : "flex-start",

          bgcolor: active
            ? "primary.50"
            : "transparent",
          overflow: "hidden",
          transition:
            "all .2s ease",

          "&:hover": {
            bgcolor: "action.hover",
          },

          ...sx,
        }}
      >
        <ListItemIcon
          sx={{
            minWidth:
              collapsed && !isDrawer
                ? 0
                : 40,

            mr:
              collapsed && !isDrawer
                ? 0
                : 1,

            justifyContent: "center",

            color: active
              ? "primary.main"
              : "text.secondary",
          }}
        >
          <Icon fontSize="small" />
        </ListItemIcon>

        {(!collapsed || isDrawer) && (
          <>
            <ListItemText
              primary={label}
              sx={{
                flex: 1,
                minWidth: 0,
              }}
              primaryTypographyProps={{
                noWrap: true,
                fontWeight: active ? 600 : 500,
                sx: {
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                },
              }}
            />

            {expandable &&
              (expanded ? (
                <ExpandLessIcon fontSize="small" />
              ) : (
                <ExpandMoreIcon fontSize="small" />
              ))}
          </>
        )}
      </ListItemButton>
    </Tooltip>
  );
}

export default SidebarItem;