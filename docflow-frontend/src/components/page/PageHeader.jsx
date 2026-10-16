import { Box, Typography } from "@mui/material";

function PageHeader({
  title,
  subtitle,
  actions,
}) {
  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: {
          xs: "flex-start",
          md: "center",
        },
        flexDirection: {
          xs: "column",
          md: "row",
        },
        gap: 2,
      }}
    >
      <Box>
        <Typography
          variant="h4"
          sx={{
            fontWeight: 700,
          }}
        >
          {title}
        </Typography>

        {subtitle && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              mt: 0.5,
            }}
          >
            {subtitle}
          </Typography>
        )}
      </Box>

      {actions && (
        <Box>
          {actions}
        </Box>
      )}
    </Box>
  );
}

export default PageHeader;