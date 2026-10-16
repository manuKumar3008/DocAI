import { useState } from "react";
import { Controller } from "react-hook-form";

import {
  IconButton,
  InputAdornment,
  TextField,
} from "@mui/material";

import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";

function PasswordInput({
  name,
  control,
  label,
  disabled = false,
}) {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <Controller
      name={name}
      control={control}
      defaultValue=""
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          fullWidth
          margin="normal"
          label={label}
          disabled={disabled}
          type={showPassword ? "text" : "password"}
          error={!!fieldState.error}
          helperText={fieldState.error?.message}
          slotProps={{
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton
                    edge="end"
                    onClick={() =>
                      setShowPassword((prev) => !prev)
                    }
                  >
                    {showPassword ? (
                      <VisibilityOff />
                    ) : (
                      <Visibility />
                    )}
                  </IconButton>
                </InputAdornment>
              ),
            },
          }}
        />
      )}
    />
  );
}

export default PasswordInput;