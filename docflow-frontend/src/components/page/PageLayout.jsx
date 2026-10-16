// src/components/page/PageLayout.jsx

import { Box, Stack, Typography } from "@mui/material";

function PageLayout({
  title,
  subtitle,
  actions,
  children,
  spacing = 3,
}) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        minHeight: 0,
        gap: spacing,
      }}
    >
      {(title || subtitle || actions) && (
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="flex-start"
          spacing={2}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            {title && (
              <Typography variant="h4">
                {title}
              </Typography>
            )}

            {subtitle && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ mt: 0.5 }}
              >
                {subtitle}
              </Typography>
            )}
          </Box>

          {actions && (
            <Box
              sx={{
                flexShrink: 0,
              }}
            >
              {actions}
            </Box>
          )}
        </Stack>
      )}

      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          display: "flex",
          flexDirection: "column",
        }}
      >
        {children}
      </Box>
    </Box>
  );
}

export default PageLayout;