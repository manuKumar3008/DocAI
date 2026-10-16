import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { Provider } from "react-redux";

import { CssBaseline, ThemeProvider } from "@mui/material";

import App from "./App";
import theme from "./theme/theme";
import { SidebarProvider } from "./contexts/SidebarContext";
import { store } from "./app/store";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <Provider store={store}>
      <SidebarProvider>
        <ThemeProvider theme={theme}>
          <BrowserRouter>
            <CssBaseline />
            <App />
          </BrowserRouter>
        </ThemeProvider>
      </SidebarProvider>
    </Provider>
  </StrictMode>
);