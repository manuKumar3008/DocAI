import { Controller } from "react-hook-form";
import { TextField } from "@mui/material";

function TextInput({
  name,
  control,
  label,
  type = "text",
  disabled = false,
  ...rest
}) {
  return (
    <Controller
      name={name}
      control={control}
      defaultValue=""
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          {...rest}
          fullWidth
          label={label}
          type={type}
          disabled={disabled}
          margin="normal"
          error={!!fieldState.error}
          helperText={fieldState.error?.message}
          InputLabelProps={{
            ...(rest.InputLabelProps || {}),
            shrink: type === "date",
          }}
        />
      )}
    />
  );
}

export default TextInput;