import { Button, CircularProgress } from "@mui/material";

function SubmitButton({
  label,
  loading = false,
}) {
  return (
    <Button
      fullWidth
      size="large"
      type="submit"
      variant="contained"
      disabled={loading}
      sx={{
        mt: 2,
        height: 48,
      }}
    >
      {loading ? (
        <CircularProgress
          size={22}
          color="inherit"
        />
      ) : (
        label
      )}
    </Button>
  );
}

export default SubmitButton;