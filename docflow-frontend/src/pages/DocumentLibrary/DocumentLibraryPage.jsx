import {
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  Button,
  Typography,
  Stack,
  Box,
} from "@mui/material";

import RefreshIcon from "@mui/icons-material/Refresh";
import AddCommentOutlinedIcon from "@mui/icons-material/AddCommentOutlined";

import {
  useAppDispatch,
  useAppSelector,
} from "../../hooks";

import {
  fetchIndexedDocuments,
} from "../../features/document-library/thunk/documentLibraryThunk";

import {
  setSearch,
  toggleDocumentSelection,
  clearSelection,
  selectAllDocuments,
} from "../../features/document-library/slice/documentLibrarySlice";

import {
  PageLayout,
  PageHeader,
  PageToolbar,
} from "../../components/page";

import {
  TableLayout,
  AppTable,
} from "../../components/table";

import SearchInput from "../../components/common/form/SearchInput";
import { useNavigate } from "react-router-dom";

import {
  createConversation,
  fetchConversations,
} from "../../features/conversations/thunk/conversationThunk";

import {
  getDocumentLibraryColumns,
} from "../../features/document-library/columns/documentLibraryColumns";

function DocumentLibraryPage() {
  const dispatch = useAppDispatch();

  const {
    documents,
    loading,
    search,
    selectedDocuments,
    total,
  } = useAppSelector(
    (state) => state.documentLibrary
  );

  const navigate = useNavigate();

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] =
    useState(10);

  useEffect(() => {
    dispatch(
      fetchIndexedDocuments({
        page,
        size: rowsPerPage,
      })
    );
  }, [dispatch, page, rowsPerPage]);

  useEffect(() => {
    setPage(0);
  }, [search]);

  const filteredDocuments = useMemo(() => {
    if (!search) return documents;

    const keyword = search.toLowerCase();

    return documents.filter(
      (document) =>
        document.fileName
          ?.toLowerCase()
          .includes(keyword) ||
        document.documentId
          ?.toLowerCase()
          .includes(keyword) ||
        document.documentType
          ?.toLowerCase()
          .includes(keyword)
    );
  }, [documents, search]);

  const handleRefresh = () => {
    dispatch(
      fetchIndexedDocuments({
        page,
        size: rowsPerPage,
      })
    );
  };

  const handleToggleSelection = (
    document
  ) => {
    dispatch(
      toggleDocumentSelection(document)
    );
  };

  const handleToggleAll = (
    checked
  ) => {
    if (checked) {
      dispatch(
        selectAllDocuments(
          filteredDocuments
        )
      );
    } else {
      dispatch(clearSelection());
    }
  };

  const handleNewConversation =
    async () => {
      if (
        selectedDocuments.length === 0
      ) {
        return;
      }

      const payload = {
        documentIds:
          selectedDocuments.map(
            (document) =>
              document.documentId
          ),

        title:
          selectedDocuments.length ===
            1
            ? selectedDocuments[0].fileName.replace(
              /\.[^/.]+$/,
              ""
            )
            : `${selectedDocuments.length} Documents`,
      };

      const result = await dispatch(
        createConversation(payload)
      );

      if (
        createConversation.fulfilled.match(
          result
        )
      ) {
        // Refresh sidebar conversations
        await dispatch(
          fetchConversations()
        );

        // Clear selected documents
        dispatch(clearSelection());

        // Navigate to conversation page
        navigate(
          `/conversation/${result.payload.conversationId}`
        );
      }
    };

  return (
    <PageLayout>
      <PageHeader
        title="Document Library"
        subtitle="Browse indexed documents ready for AI processing."
      />
      <Box sx={{ mb: 3 }}>
        <PageToolbar
          left={
            <SearchInput
              value={search}
              placeholder="Search documents..."
              onChange={(e) =>
                dispatch(
                  setSearch(
                    e.target.value
                  )
                )
              }
            />
          }
          right={
            <Stack
              direction="row"
              spacing={2}
              alignItems="center"
            >
              <Button
                variant="outlined"
                startIcon={
                  <RefreshIcon />
                }
                onClick={
                  handleRefresh
                }
                disabled={loading}
              >
                Refresh
              </Button>

              {selectedDocuments.length >
                0 && (
                  <>
                    <Typography
                      fontWeight={600}
                    >
                      {
                        selectedDocuments.length
                      }{" "}
                      Selected
                    </Typography>

                    <Button
                      variant="contained"
                      startIcon={
                        <AddCommentOutlinedIcon />
                      }
                      onClick={
                        handleNewConversation
                      }
                    >
                      + New Conversation
                    </Button>
                  </>
                )}
            </Stack>
          }
        />
      </Box>

      <TableLayout>
        <AppTable
          rows={filteredDocuments}
          loading={loading}
          getRowId={(row) =>
            row.documentId
          }
          columns={getDocumentLibraryColumns({
            documents:
              filteredDocuments,
            selectedDocuments,
            onToggleSelection:
              handleToggleSelection,
            onToggleAll:
              handleToggleAll,
          })}
          emptyMessage="No indexed documents found."
          pagination={{
            page,
            rowsPerPage,
            total,
            onPageChange: (
              _,
              newPage
            ) => {
              setPage(newPage);
            },
            onRowsPerPageChange: (
              event
            ) => {
              setRowsPerPage(
                parseInt(
                  event.target.value,
                  10
                )
              );
              setPage(0);
            },
          }}
        />
      </TableLayout>
    </PageLayout>
  );
}

export default DocumentLibraryPage;