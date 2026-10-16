import {
  Controller,
} from "react-hook-form";

import {
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  FormHelperText,
} from "@mui/material";

function SelectInput({
  control,
  name,
  label,
  options = [],
  disabled = false,
  fullWidth = true,
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({
        field,
        fieldState: { error },
      }) => (
        <FormControl
          fullWidth={fullWidth}
          error={!!error}
          disabled={disabled}
        >
          <InputLabel>{label}</InputLabel>

          <Select
            {...field}
            label={label}
          >
            {options.map((option) => (
              <MenuItem
                key={String(option.value)}
                value={option.value}
              >
                {option.label}
              </MenuItem>
            ))}
          </Select>

          {error && (
            <FormHelperText>
              {error.message}
            </FormHelperText>
          )}
        </FormControl>
      )}
    />
  );
}

export default SelectInput;