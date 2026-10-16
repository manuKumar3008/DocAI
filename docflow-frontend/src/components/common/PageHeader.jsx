import { Box, Typography } from "@mui/material";

function PageHeader({ title, description }) {
  return (
    <Box sx={{ mb: 4 }}>
      <Typography
        variant="h3"
        sx={{
          fontWeight: 700,
          mb: 0.5,
        }}
      >
        {title}
      </Typography>

      {description && (
        <Typography
          variant="body2"
          color="text.secondary"
        >
          {description}
        </Typography>
      )}
    </Box>
  );
}

export default PageHeader;