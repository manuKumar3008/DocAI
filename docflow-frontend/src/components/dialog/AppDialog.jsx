import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Typography,
} from "@mui/material";

import CloseIcon from "@mui/icons-material/Close";

function AppDialog({
  open,
  title,
  children,
  actions,
  onClose,
  loading = false,
  maxWidth = "sm",
  fullWidth = true,
  fullScreen = false,
}) {
  return (
    <Dialog
      open={open}
      onClose={loading ? undefined : onClose}
      maxWidth={maxWidth}
      fullWidth={fullWidth}
      fullScreen={fullScreen}
      PaperProps={{
        sx: {
          borderRadius: 3,
        },
      }}
    >
      <DialogTitle
        sx={{
          px: 3,
          py: 2,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
      >
        <Typography
          variant="h6"
          fontWeight={600}
        >
          {title}
        </Typography>

        <IconButton
          size="small"
          onClick={onClose}
          disabled={loading}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent
        dividers
        sx={{
          p: 3,
        }}
      >
        {children}
      </DialogContent>

      {actions && (
        <DialogActions
          sx={{
            px: 3,
            py: 2,
            borderTop: "1px solid",
            borderColor: "divider",
          }}
        >
          {actions}
        </DialogActions>
      )}
    </Dialog>
  );
}

export default AppDialog;