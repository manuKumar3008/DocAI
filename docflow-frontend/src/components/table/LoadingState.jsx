// src/components/table/LoadingState.jsx

import {
  Skeleton,
  TableBody,
  TableCell,
  TableRow,
} from "@mui/material";

function LoadingState({
  columns = 5,
  rows = 8,
}) {
  return (
    <TableBody>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <TableRow key={rowIndex}>
          {Array.from({ length: columns }).map((_, colIndex) => (
            <TableCell key={colIndex}>
              <Skeleton
                variant="text"
                height={28}
              />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </TableBody>
  );
}

export default LoadingState;