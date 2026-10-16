// src/features/users/components/ResetPasswordForm.jsx

import { Box } from "@mui/material";

import {
  PasswordInput,
} from "../../../components/common/form";

function ResetPasswordForm({
  control,
}) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 3,
        mt: 1,
      }}
    >
      <PasswordInput
        control={control}
        name="newPassword"
        label="New Password"
      />

      <PasswordInput
        control={control}
        name="confirmPassword"
        label="Confirm New Password"
      />
    </Box>
  );
}

export default ResetPasswordForm;