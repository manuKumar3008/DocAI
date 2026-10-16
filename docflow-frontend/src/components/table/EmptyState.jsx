// src/components/table/EmptyState.jsx

import {
  TableBody,
  TableCell,
  TableRow,
  Typography,
  Box,
} from "@mui/material";

import InboxOutlinedIcon from "@mui/icons-material/InboxOutlined";

function EmptyState({
  columns,
  message = "No records found",
}) {
  return (
    <TableBody>
      <TableRow>
        <TableCell colSpan={columns}>
          <Box
            sx={{
              py: 8,
              textAlign: "center",
            }}
          >
            <InboxOutlinedIcon
              sx={{
                fontSize: 56,
                color: "text.disabled",
                mb: 2,
              }}
            />

            <Typography
              variant="h6"
              fontWeight={600}
            >
              {message}
            </Typography>

            <Typography
              variant="body2"
              color="text.secondary"
            >
              Try changing filters or refreshing.
            </Typography>
          </Box>
        </TableCell>
      </TableRow>
    </TableBody>
  );
}

export default EmptyState;