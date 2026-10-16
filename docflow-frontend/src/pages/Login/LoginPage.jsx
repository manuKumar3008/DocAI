import { Box } from "@mui/material";

import LoginCard from "../../features/auth/components/LoginCard";

function LoginPage() {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        px: 2,
      }}
    >
      <LoginCard />
    </Box>
  );
}

export default LoginPage;