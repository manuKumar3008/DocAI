// src/components/table/AppPagination.jsx

import { TablePagination } from "@mui/material";

function AppPagination({
  page,
  rowsPerPage,
  total,
  onPageChange,
  onRowsPerPageChange,
}) {
  return (
    <TablePagination
      component="div"
      page={page}
      count={total}
      rowsPerPage={rowsPerPage}
      rowsPerPageOptions={[10, 20, 50]}
      onPageChange={onPageChange}
      onRowsPerPageChange={onRowsPerPageChange}
    />
  );
}

export default AppPagination;