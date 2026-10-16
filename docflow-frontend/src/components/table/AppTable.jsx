// src/components/table/AppTable.jsx

import {
  Paper,
  Table,
  TableContainer,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Box,
} from "@mui/material";

import LoadingState from "./LoadingState";
import EmptyState from "./EmptyState";
import AppPagination from "./AppPagination";

function AppTable({
  columns,
  rows = [],
  loading = false,
  getRowId,
  emptyMessage = "No records found.",
  pagination,
}) {
  return (
    <Paper
      elevation={0}
      sx={{
        display: "flex",
        flexDirection: "column",

        flex: 1,
        minHeight: 0,
        height: "100%",

        border: "1px solid",
        borderColor: "divider",
        borderRadius: 3,

        overflow: "auto",
      }}
    >
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflow: "auto",
        }}
      >
        <TableContainer
          sx={{
            height: "100%",
            overflow: "auto",

            "&::-webkit-scrollbar": {
              width: 8,
              height: 8,
            },

            "&::-webkit-scrollbar-thumb": {
              backgroundColor: "#C5CBD5",
              borderRadius: 8,
            },
          }}
        >
          <Table stickyHeader>
            <TableHead>
              <TableRow>
                {columns.map((column) => (
                  <TableCell
                    key={column.field}
                    sx={{
                      fontWeight: 700,
                      bgcolor: "grey.50",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {column.headerName}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>

            {loading ? (
              <LoadingState columns={columns.length} />
            ) : rows.length === 0 ? (
              <EmptyState
                columns={columns.length}
                message={emptyMessage}
              />
            ) : (
              <TableBody>
                {rows.map((row) => (
                  <TableRow
                    hover
                    key={getRowId(row)}
                    sx={{
                      cursor: "pointer",

                      "&:hover": {
                        bgcolor: "#F7FAFF",
                      },
                    }}
                  >
                    {columns.map((column) => (
                      <TableCell key={column.field}>
                        {column.renderCell
                          ? column.renderCell(row)
                          : column.render
                          ? column.render(row)
                          : row[column.field]}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>
        </TableContainer>
      </Box>

      {pagination && <AppPagination {...pagination} />}
    </Paper>
  );
}

export default AppTable;