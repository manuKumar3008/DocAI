import {
  Box,
  Card,
  CardContent,
  Divider,
  Typography,
} from "@mui/material";

import LoginForm from "./LoginForm";

function LoginCard() {
  return (
    <Card
      elevation={0}
      sx={{
        width: "100%",
        maxWidth: 460,
        borderRadius: 3,
        border: "1px solid",
        borderColor: "divider",
        bgcolor: "background.paper",
        transition: "all .25s ease",

        "&:hover": {
          boxShadow: "0 12px 30px rgba(0,102,204,0.08)",
          transform: "translateY(-2px)",
        },
      }}
    >
      <CardContent
        sx={{
          p: 5,
        }}
      >
        <Typography
          variant="h4"
          sx={{
            fontWeight: 700,
            color: "primary.main",
            mb: 1,
          }}
        >
          DocFlow AI
        </Typography>

        <Typography
          variant="h6"
          sx={{
            fontWeight: 600,
            mb: 0.5,
          }}
        >
          Welcome Back
        </Typography>

        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            mb: 4,
          }}
        >
          Sign in to continue to your Document Intelligence Platform.
        </Typography>

        <Divider sx={{ mb: 4 }} />

        <LoginForm />

        <Box
          sx={{
            mt: 4,
            textAlign: "center",
          }}
        >
          <Typography
            variant="caption"
            color="text.secondary"
          >
            © 2026 DocFlow AI • Version 1.0.0
          </Typography>
        </Box>
      </CardContent>
    </Card>
  );
}

export default LoginCard;