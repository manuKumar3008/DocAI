import {
  FormControlLabel,
  Paper,
  Switch,
  Typography,
} from "@mui/material";

function ForceReindexToggle({
  checked,
  onChange,
}) {
  return (
    <Paper
      variant="outlined"
      sx={{
        mt: 3,
        p: 2,
        borderRadius: 2,
      }}
    >
      <FormControlLabel
        control={
          <Switch
            checked={checked}
            onChange={(event) =>
              onChange(
                event.target.checked
              )
            }
          />
        }
        label={
          <Typography fontWeight={600}>
            Force Reindex
          </Typography>
        }
      />

      <Typography
        variant="body2"
        color="text.secondary"
      >
        Enable this only when you want
        to overwrite the existing
        indexed document.
      </Typography>
    </Paper>
  );
}

export default ForceReindexToggle;