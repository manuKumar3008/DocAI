import { Box, Typography } from "@mui/material";

function Logo() {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
      }}
    >
      <Box
        sx={{
          width: 36,
          height: 36,
          borderRadius: 2,
          bgcolor: "primary.main",
        }}
      />

      <Typography
        variant="h4"
        sx={{
          fontWeight: 700,
          letterSpacing: 0.3,
        }}
      >
        DocFlow AI
      </Typography>
    </Box>
  );
}

export default Logo;