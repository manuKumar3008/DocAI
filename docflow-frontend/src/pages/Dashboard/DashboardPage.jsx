// src/pages/Dashboard/DashboardPage.jsx

import { useEffect } from "react";

import {
  useAppDispatch,
  useAppSelector,
} from "../../hooks";

import {
  fetchDocuments,
} from "../../features/dashboard/thunk/dashboardThunk";

import {
  setPage,
  setRowsPerPage,
  setStatusFilter,
  setDocumentTypeFilter,
} from "../../features/dashboard/slice/dashboardSlice";

import DashboardToolbar from "../../features/dashboard/components/DashboardToolbar";

import { documentColumns } from "../../features/dashboard/columns/documentColumns";

import {
  PageLayout,
  PageHeader,
} from "../../components/page";

import {
  AppTable,
  TableLayout,
} from "../../components/table";

function DashboardPage() {
  const dispatch = useAppDispatch();

  const {
    documents,
    loading,
    total,
    page,
    size,
    filters,
  } = useAppSelector(
    (state) => state.dashboard
  );

  useEffect(() => {
    dispatch(
      fetchDocuments({
        page,
        size,
        status: filters.status,
        documentType: filters.documentType,
      })
    );
  }, [
    dispatch,
    page,
    size,
    filters.status,
    filters.documentType,
  ]);

  const handleRefresh = () => {
    dispatch(
      fetchDocuments({
        page,
        size,
        status: filters.status,
        documentType: filters.documentType,
      })
    );
  };

  return (
    <PageLayout>
      <PageHeader
        title="Documents"
        subtitle="Manage indexed documents"
      />

      <DashboardToolbar
        loading={loading}
        status={filters.status}
        documentType={filters.documentType}
        onRefresh={handleRefresh}
        onStatusChange={(value) =>
          dispatch(setStatusFilter(value))
        }
        onDocumentTypeChange={(value) =>
          dispatch(setDocumentTypeFilter(value))
        }
      />

      <TableLayout>
        <AppTable
          columns={documentColumns}
          rows={documents}
          loading={loading}
          emptyMessage="No documents found."
          getRowId={(row) => row.documentId}
          pagination={{
            page,
            rowsPerPage: size,
            total,
            onPageChange: (_, newPage) =>
              dispatch(setPage(newPage)),
            onRowsPerPageChange: (event) =>
              dispatch(
                setRowsPerPage(
                  Number(event.target.value)
                )
              ),
          }}
        />
      </TableLayout>
    </PageLayout>
  );
}

export default DashboardPage;