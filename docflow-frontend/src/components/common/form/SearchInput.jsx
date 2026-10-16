import SearchIcon from "@mui/icons-material/Search";
import {
  InputAdornment,
  TextField,
} from "@mui/material";

function SearchInput({
  value = "",
  onChange,
  placeholder = "Search...",
  disabled = false,
  fullWidth = false,
}) {
  return (
    <TextField
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      disabled={disabled}
      size="small"
      fullWidth={fullWidth}
      sx={{
        minWidth: 320,
      }}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon
              fontSize="small"
              color="action"
            />
          </InputAdornment>
        ),
      }}
    />
  );
}

export default SearchInput;