import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  TextField,
} from "@mui/material";

const LANGUAGES = [
  "English",
  "German",
  "French",
  "Spanish",
  "Italian",
  "Portuguese",
];

function SummaryDialog({
  open,
  language,
  loading,
  onLanguageChange,
  onClose,
  onGenerate,
}) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
    >
      <DialogTitle>
        Generate Summary
      </DialogTitle>

      <DialogContent
        sx={{
          pt: "20px !important",
        }}
      >
        <TextField
          select
          fullWidth
          label="Language"
          value={language}
          onChange={(e) =>
            onLanguageChange(e.target.value)
          }
        >
          {LANGUAGES.map((lang) => (
            <MenuItem
              key={lang}
              value={lang}
            >
              {lang}
            </MenuItem>
          ))}
        </TextField>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button
          onClick={onClose}
          disabled={loading}
        >
          Cancel
        </Button>

        <Button
          variant="contained"
          onClick={onGenerate}
          disabled={loading}
        >
          {loading
            ? "Generating..."
            : "Generate"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default SummaryDialog;