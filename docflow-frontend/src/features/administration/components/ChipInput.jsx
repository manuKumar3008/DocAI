import { useState } from "react";
import {
  Box,
  Chip,
  TextField,
} from "@mui/material";

export default function ChipInput({
  value = [],
  onChange,
  placeholder,
}) {
  const [input, setInput] = useState("");

  const addChip = () => {
    const text = input.trim();

    if (!text) return;

    if (!value.includes(text)) {
      onChange([...value, text]);
    }

    setInput("");
  };

  const removeChip = (chip) => {
    onChange(value.filter((item) => item !== chip));
  };

  return (
    <Box>
      {value.length > 0 && (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "repeat(1, minmax(0, 1fr))",
              sm: "repeat(2, minmax(0, 1fr))",
              md: "repeat(4, minmax(0, 1fr))",
            },
            gap: 1.5,
            mb: 2,
          }}
        >
          {value.map((item) => (
            <Chip
              key={item}
              label={item}
              onDelete={() => removeChip(item)}
              sx={{
                width: "fit-content",
                maxWidth: "100%",
              }}
            />
          ))}
        </Box>
      )}

      <TextField
        fullWidth
        value={input}
        placeholder={placeholder}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            addChip();
          }
        }}
      />
    </Box>
  );
}