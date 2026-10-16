// src/theme/theme.js

import { createTheme } from "@mui/material/styles";

import colors from "./colors";
import typography from "./typography";
import shadows from "./shadows";

const theme = createTheme({
  palette: {
    mode: "light",

    primary: {
      main: colors.primary,
      light: colors.primaryLight,
    },

    background: {
      default: colors.background,
      paper: colors.surface,
    },

    success: {
      main: colors.success,
    },

    warning: {
      main: colors.warning,
    },

    error: {
      main: colors.error,
    },

    text: {
      primary: colors.text.primary,
      secondary: colors.text.secondary,
    },

    divider: colors.border,
  },

  typography,

  shape: {
    borderRadius: 8,
  },

  shadows,
});

export default theme;